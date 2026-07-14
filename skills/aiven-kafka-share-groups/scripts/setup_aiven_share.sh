#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

KAFKA_SERVICE="${1:?Usage: $0 <service-name>}"
TOPIC="product_photo_raw"
REB_PROTOCOLS="classic,consumer,share"
CERT_DIR="cert"
ENV_FILE="env.sh"
KEYSTORE_FILE="$CERT_DIR/client.keystore.p12"
TRUSTSTORE_FILE="$CERT_DIR/client.truststore.p12"

echo "==> Checking avn login..."
set +e
AVN_USER_INFO_OUTPUT=$(avn user info 2>&1)
AVN_USER_INFO_EXIT=$?
set -e
if [ "$AVN_USER_INFO_EXIT" -ne 0 ]; then
  case "$AVN_USER_INFO_OUTPUT" in
    *"Expired db token"*)
      echo "ERROR: Aiven login token expired. Run: avn user login <email>" >&2
      ;;
    *"ERROR: Not logged in"*|*"UserError: not authenticated"*)
      echo "ERROR: Not authenticated. Run: avn user login <email>" >&2
      ;;
    *)
      printf '%s\n' "$AVN_USER_INFO_OUTPUT" >&2
      echo "ERROR: Failed to verify avn login." >&2
      ;;
  esac
  exit 1
fi

echo "==> Downloading user credentials..."
mkdir -p "$CERT_DIR"
avn service user-creds-download "$KAFKA_SERVICE" --username avnadmin -d "$CERT_DIR"

echo "==> Building truststore from CA certificate..."
TRUSTSTORE_PASSWORD=$(openssl rand -base64 24)
rm -f "$TRUSTSTORE_FILE"
keytool -importcert -alias aiven-ca \
  -file "$CERT_DIR/ca.pem" \
  -keystore "$TRUSTSTORE_FILE" \
  -storepass "$TRUSTSTORE_PASSWORD" \
  -storetype PKCS12 \
  -noprompt

echo "==> Building keystore from client certificate..."
KEYSTORE_PASSWORD=$(openssl rand -base64 24)
# Reason: Always (re)generate the keystore from cert/key to ensure the password 
# generated above matches the one in the keystore file.
rm -f "$KEYSTORE_FILE"
openssl pkcs12 -export \
  -in "$CERT_DIR/service.cert" \
  -inkey "$CERT_DIR/service.key" \
  -name "aiven-client" \
  -out "$KEYSTORE_FILE" \
  -passout "pass:$KEYSTORE_PASSWORD"

echo "==> Enabling share-group rebalance protocols on service..."
avn service update "$KAFKA_SERVICE" \
  -c "kafka.group_coordinator_rebalance_protocols=$REB_PROTOCOLS"
avn service wait "$KAFKA_SERVICE"

echo "==> Creating topic '$TOPIC' if missing..."
if ! avn service topic-get "$KAFKA_SERVICE" "$TOPIC" >/dev/null 2>&1; then
  PLAN_NAME=$(avn service get "$KAFKA_SERVICE" --json | jq -r '.plan')
  # Reason: developer plans support replication factor 1 only.
  if [[ "$PLAN_NAME" == "developer-"* ]]; then
    REPLICATION_FACTOR=1
  else
    REPLICATION_FACTOR=2
  fi
  avn service topic-create "$KAFKA_SERVICE" "$TOPIC" \
    --partitions 3 \
    --replication "$REPLICATION_FACTOR"
fi

SERVICE_JSON=$(avn service get "$KAFKA_SERVICE" --json)
KAFKA_HOST=$(printf '%s\n' "$SERVICE_JSON" | jq -r '.service_uri_params.host')
KAFKA_PORT=$(printf '%s\n' "$SERVICE_JSON" | jq -r '.service_uri_params.port')

cat > "$ENV_FILE" <<EOF
export KAFKA_SERVICE="$KAFKA_SERVICE"
export BOOTSTRAP_SERVERS="${KAFKA_HOST}:${KAFKA_PORT}"
export SHARE_GROUP_ID="product-photo-share-group"
export TEMPLATE_JAVA_VARIANT="share_consumer_java_remote"
export SSL_KEYSTORE_PATH="$KEYSTORE_FILE"
export SSL_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export SSL_KEY_PASSWORD="$KEYSTORE_PASSWORD"
export SSL_TRUSTSTORE_PATH="$TRUSTSTORE_FILE"
export SSL_TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASSWORD"
EOF

echo "==> Setup complete."
echo "    source $ENV_FILE"
