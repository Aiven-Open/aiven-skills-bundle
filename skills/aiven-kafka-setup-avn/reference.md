# avn CLI Reference — Kafka on Aiven

Quick reference for `avn` commands used in this skill.

---

## Authentication

| Command | Description |
|---------|-------------|
| `avn user login <email>` | Log in to Aiven |
| `avn user info` | Verify current session |
| `avn user logout` | Log out |

---

## Region Mapping

| Region | CLOUD_NAME | Provider |
|--------|------------|----------|
| Asia (Singapore) | `do-sgp` | DigitalOcean |
| Australia (Sydney) | `do-syd` | DigitalOcean |
| Canada (Toronto) | `do-tor` | DigitalOcean |
| Europe (Frankfurt) | `do-fra` | DigitalOcean |
| United States (New York) | `do-nyc` | DigitalOcean |

---

## Service Management

```bash
# List available plans for a region
avn service plans --service-type kafka --cloud <CLOUD_NAME>

# Create Kafka service with SASL, Schema Registry, and explicit version
avn service create <SERVICE_NAME> \
  --service-type kafka \
  --cloud <CLOUD_NAME> \
  --plan startup-4 \
  --no-project-vpc \
  --kafka-version 4.1 \
  -c kafka_authentication_methods.sasl=true \
  -c schema_registry=true

# Wait until service is RUNNING
avn service wait <SERVICE_NAME>

# Tag the service for AI-generated demo environments
avn service tags update <SERVICE_NAME> --add-tag AI-skill-generated=true

# Get service details as JSON (preferred — use jq to extract fields)
avn service get <SERVICE_NAME> --json | jq '{service_uri, service_uri_params, components: [.components[] | {component, route, host, port}]}'

# Extract host and port
avn service get <SERVICE_NAME> --json | jq -r '.service_uri_params.host'
avn service get <SERVICE_NAME> --json | jq -r '.service_uri_params.port'

# Extract Schema Registry port
avn service get <SERVICE_NAME> --json \
  | jq -r '.components[] | select(.route == "dynamic" and .component == "schema_registry") | .port'

# Delete service
avn service terminate <SERVICE_NAME> --force
```

---

## Users & Credentials

```bash
# Download certs for a user
avn service user-creds-download <SERVICE_NAME> --username <USER> -d cert/

# Create a new service user
avn service user-create <SERVICE_NAME> --username <USER>

# Extract password safely into env var (NEVER print raw output to agent context)
export PRODUCER_PASSWORD=$(avn service user-list <SERVICE_NAME> --json \
  | jq -r '.[] | select(.username == "producer-user") | .password')

# Verify password by length only
echo "PRODUCER_PASSWORD length: ${#PRODUCER_PASSWORD}"
```

---

## Topics

```bash
# Create topic
avn service topic-create <SERVICE_NAME> <TOPIC> \
  --partitions 3 \
  --replication 2

# List topics
avn service topic-list <SERVICE_NAME>

# Delete topic
avn service topic-delete <SERVICE_NAME> <TOPIC>
```

---

## ACLs

```bash
# Add topic ACL
avn service acl-add <SERVICE_NAME> \
  --username <USER> \
  --topic <TOPIC> \
  --permission <read|write|readwrite>

# List topic ACLs
avn service acl-list <SERVICE_NAME>

# Add Schema Registry ACL
avn service schema-registry-acl-add <SERVICE_NAME> \
  --username <USER> \
  --resource "Subject:<SUBJECT>" \
  --permission <schema_registry_read|schema_registry_write>

# List Schema Registry ACLs
avn service schema-registry-acl-list <SERVICE_NAME>
```

---

## Schema Registry

> **NOTE**: `avn service schema-registry-subject-version-create` does NOT exist in
> avn CLI v4.7.0+. Always use the **REST API** for schema registration.

```bash
# List subjects (this avn subcommand works)
avn service schema-registry-subject-list <SERVICE_NAME>
```

### Via avn CLI (required for registration)

```bash
# Register schema
avn service schema create <SERVICE_NAME> \
  --subject <SUBJECT> \
  --schema "$(cat schema.avsc)"

# Get schema
avn service schema get <SERVICE_NAME> --subject <SUBJECT> --version latest
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `avn user info` fails with `Expired db token` | Login token expired | Run `avn user login <email>` again |
| `avn user info` fails with `ERROR: Not logged in` or `UserError: not authenticated` | Not authenticated yet | Run `avn user login <email>` or create an account via `https://console.aiven.io/login` |
| Service stuck in POWERING_OFF | Ongoing operations | Wait or contact Aiven support |
| `SASL authentication failed` | Wrong password or user not created | Re-run `avn service user-password-reset` and re-export env var |
| `SSL: CERTIFICATE_VERIFY_FAILED` | Missing or wrong CA cert | Re-download certs: `avn service user-creds-download` |
| Schema Registry 401 | ACL not set for user | Add SR ACL with `avn service schema-registry-acl-add` |
| Schema Registry 409 | Incompatible schema evolution | Check compatibility mode or delete subject and re-register |
| `Topic not found` | Topic not created or wrong name | Verify with `avn service topic-list` |
| Consumer gets no messages | Consumer started after producer finished | Start consumer before producer; check `--from-beginning` flag |

---

## Environment Variables

The `setup_aiven_kafka.sh` script automatically extracts all connection details and
writes them to `env.sh`. To load them:

```bash
source env.sh
```

| Variable | Source |
|----------|--------|
| `KAFKA_SERVICE` | Script argument |
| `KAFKA_HOST` | `jq .service_uri_params.host` |
| `KAFKA_PORT` | `jq .service_uri_params.port` |
| `SCHEMA_REGISTRY_URL` | Constructed from host + SR port |
| `AVNADMIN_PASS` | `jq` from user-list *(never printed)* |
| `PRODUCER_PASSWORD` | `jq` from user-list *(never printed)* |
| `CONSUMER_PASSWORD` | `jq` from user-list *(never printed)* |

To re-extract manually (e.g. if `env.sh` was deleted):

```bash
export KAFKA_HOST=$(avn service get "$KAFKA_SERVICE" --json | jq -r '.service_uri_params.host')
export KAFKA_PORT=$(avn service get "$KAFKA_SERVICE" --json | jq -r '.service_uri_params.port')
export SCHEMA_REGISTRY_URL="https://${KAFKA_HOST}:$(avn service get "$KAFKA_SERVICE" --json \
  | jq -r '.components[] | select(.route == "dynamic" and .component == "schema_registry") | .port')"
```
