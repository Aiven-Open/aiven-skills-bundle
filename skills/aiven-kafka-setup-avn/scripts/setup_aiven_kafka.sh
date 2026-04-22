#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

KAFKA_SERVICE="${1:?Usage: $0 <service-name> <cloud-name> [plan] [kafka-version]}"
CLOUD_NAME="${2:?Usage: $0 <service-name> <cloud-name> [plan] [kafka-version]}"
PLAN="${3:-startup-4}"
KAFKA_VERSION="${4:-4.1}"
TOPIC="orders"

echo "==> Checking avn login..."
set +e
AVN_USER_INFO_OUTPUT=$(avn user info 2>&1)
AVN_USER_INFO_EXIT=$?
set -e

if [ "$AVN_USER_INFO_EXIT" -ne 0 ]; then
  # Reason: `avn user info` uses different error messages for expired tokens
  # and users who have not authenticated yet, so surface the right next step.
  case "$AVN_USER_INFO_OUTPUT" in
    *"Expired db token"*)
      echo "ERROR: Aiven login token expired. Run: avn user login <email>" >&2
      ;;
    *"ERROR: Not logged in"*|*"UserError: not authenticated"*)
      echo "ERROR: Not authenticated. Run: avn user login <email> or, if you are a new user, create an account via https://console.aiven.io/login" >&2
      ;;
    *)
      printf '%s\n' "$AVN_USER_INFO_OUTPUT" >&2
      echo "ERROR: Failed to verify avn login. Run: avn user login <email> if needed." >&2
      ;;
  esac
  exit 1
fi

echo "==> Creating Kafka service: $KAFKA_SERVICE (cloud=$CLOUD_NAME, plan=$PLAN, kafka=$KAFKA_VERSION)"
if ! avn service get "$KAFKA_SERVICE" >/dev/null 2>&1; then
  avn service create "$KAFKA_SERVICE" \
    --service-type kafka \
    --cloud "$CLOUD_NAME" \
    --plan "$PLAN" \
    --no-project-vpc \
    -c kafka_version="$KAFKA_VERSION" \
    -c kafka_authentication_methods.sasl=true \
    -c schema_registry=true
fi

echo "==> Waiting for service to be RUNNING..."
avn service wait "$KAFKA_SERVICE"

echo "==> Tagging service..."
# Reason: `avn` expects tags in `key=value` form rather than a bare label.
avn service tags update "$KAFKA_SERVICE" --add-tag AI-skill-generated=true

echo "==> Downloading certificates..."
mkdir -p cert
avn service user-creds-download "$KAFKA_SERVICE" --username avnadmin -d cert/

echo "==> Building PKCS12 truststore from CA certificate..."
TRUSTSTORE_PASS=$(openssl rand -base64 24)
rm -f cert/truststore.p12
keytool -importcert -alias aiven-ca \
  -file cert/ca.pem \
  -keystore cert/truststore.p12 \
  -storepass "$TRUSTSTORE_PASS" \
  -storetype PKCS12 \
  -noprompt
echo "    truststore.p12 created (password length=${#TRUSTSTORE_PASS})"

echo "==> Creating producer-user..."
if ! avn service user-get "$KAFKA_SERVICE" --username producer-user >/dev/null 2>&1; then
  avn service user-create "$KAFKA_SERVICE" --username producer-user
fi

echo "==> Creating consumer-user..."
if ! avn service user-get "$KAFKA_SERVICE" --username consumer-user >/dev/null 2>&1; then
  avn service user-create "$KAFKA_SERVICE" --username consumer-user
fi

echo "==> Creating topic: $TOPIC"
if ! avn service topic-get "$KAFKA_SERVICE" "$TOPIC" >/dev/null 2>&1; then
  avn service topic-create "$KAFKA_SERVICE" "$TOPIC" \
    --partitions 3 \
    --replication 2
fi

echo "==> Setting Kafka ACLs..."
avn service kafka-acl-add "$KAFKA_SERVICE" \
  --principal User:producer-user --operation Write --topic "$TOPIC" || true
avn service kafka-acl-add "$KAFKA_SERVICE" \
  --principal User:consumer-user --operation Read --topic "$TOPIC" || true
avn service kafka-acl-add "$KAFKA_SERVICE" \
  --principal User:consumer-user --operation Read --group "orders-consumer-group" || true

echo "==> Setting Schema Registry ACLs..."
avn service schema-registry-acl-add "$KAFKA_SERVICE" \
  --username producer-user --resource "Subject:${TOPIC}-value" --permission schema_registry_write || true
avn service schema-registry-acl-add "$KAFKA_SERVICE" \
  --username consumer-user --resource "Subject:${TOPIC}-value" --permission schema_registry_read || true

echo "==> Fetching connection details and passwords..."
SR_HOST=$(avn service get "$KAFKA_SERVICE" --json | jq -r '.service_uri_params.host')
SR_PORT=$(avn service get "$KAFKA_SERVICE" --json \
  | jq -r '.components[] | select(.route == "dynamic" and .component == "schema_registry") | .port')
KAFKA_PORT=$(avn service get "$KAFKA_SERVICE" --json \
  | jq -r '.components[] | select(.route == "dynamic" and .component == "kafka" and .kafka_authentication_method == "sasl") | .port')

AVNADMIN_PW=$(avn service user-get "$KAFKA_SERVICE" --username avnadmin --json | jq -r '.password')
PRODUCER_PW=$(avn service user-get "$KAFKA_SERVICE" --username producer-user --json | jq -r '.password')
CONSUMER_PW=$(avn service user-get "$KAFKA_SERVICE" --username consumer-user --json | jq -r '.password')

echo "==> Registering Avro schema via avn CLI..."
SCHEMA_STR=$(cat templates/order.avsc)
set +e
avn service schema create "$KAFKA_SERVICE" \
  --subject "${TOPIC}-value" \
  --schema "$SCHEMA_STR" >/dev/null 2>&1
AVN_EXIT=$?
set -e

if [ "$AVN_EXIT" -eq 0 ]; then
  echo "    Schema registered successfully."
else
  echo "    WARNING: Schema registration failed or already exists."
fi

ENV_FILE="env.sh"
cat > "$ENV_FILE" <<EOF
export KAFKA_SERVICE="$KAFKA_SERVICE"
export KAFKA_HOST="$SR_HOST"
export KAFKA_PORT="$KAFKA_PORT"
export SCHEMA_REGISTRY_URL="https://${SR_HOST}:${SR_PORT}"
export AVNADMIN_PASS="$AVNADMIN_PW"
export PRODUCER_PASSWORD="$PRODUCER_PW"
export CONSUMER_PASSWORD="$CONSUMER_PW"
export TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASS"
export TRUSTSTORE_PATH="cert/truststore.p12"
EOF

unset AVNADMIN_PW PRODUCER_PW CONSUMER_PW TRUSTSTORE_PASS

echo "==> Verifying env.sh..."
ERRORS=0
for var in KAFKA_HOST KAFKA_PORT SCHEMA_REGISTRY_URL; do
  val=$(grep "^export ${var}=" "$ENV_FILE" | cut -d'"' -f2)
  if [ -z "$val" ]; then
    echo "    FAIL: $var is empty"
    ERRORS=$((ERRORS + 1))
  else
    echo "    $var=$val"
  fi
done
for var in AVNADMIN_PASS PRODUCER_PASSWORD CONSUMER_PASSWORD TRUSTSTORE_PASSWORD TRUSTSTORE_PATH; do
  val=$(grep "^export ${var}=" "$ENV_FILE" | cut -d'"' -f2)
  len=${#val}
  if [ "$len" -eq 0 ]; then
    echo "    FAIL: $var is empty"
    ERRORS=$((ERRORS + 1))
  else
    echo "    $var length=$len"
  fi
done

if [ "$ERRORS" -gt 0 ]; then
  echo ""
  echo "ERROR: $ERRORS variable(s) failed verification. Check avn service status."
  exit 1
fi

echo ""
echo "==> Setup complete! To load environment variables, run:"
echo ""
echo "    source env.sh"
