---
name: aiven-kafka-share-groups
description: >
  Use when a user wants to create a Kafka Share Group example, get started with
  share groups, or quickly spin up a new share-group consumer. Builds and runs a
  Java Share Group consumer demo against Apache Kafka 4.2 using KafkaShareConsumer
  (KIP-932), on localhost (PLAINTEXT) or an existing Aiven Kafka service (SSL/mTLS).
version: "0.1.0"
license: Apache-2.0
allowed-tools: Bash(avn:*) Bash(jq:*) Bash(keytool:*) Bash(openssl:*) Bash(mvn:*) Bash(java:*) Bash(bash:*) Read
---

# Aiven Kafka Share Groups Consumer

End-to-end workflow for running a Java share-group consumer demo with
`KafkaShareConsumer` on Apache Kafka 4.2.

> **IMPORTANT**: This skill does not create a Kafka service. It connects either
> to localhost (`PLAINTEXT`) or to an already existing Aiven Kafka service
> (`SSL` with client certs). The workflow first sends a few probe records to
> guarantee topic existence, then starts the share consumer, then starts the
> main producer so consumption is visible immediately.

---

## Step 1: Prerequisites

Verify required tools:
- `java` (17+)
- `mvn`

Only when the user chooses **Aiven service** also verify:
- `avn` CLI (4.7.0+ recommended)
- Active `avn` login (`avn user info`)

If `avn user info` fails:
- If it contains `Expired db token`, ask the user to run
  `avn user login <email>`.
- If it contains `ERROR: Not logged in` or `UserError: not authenticated`,
  ask the user to run `avn user login <email>` (or create an account at
  `https://console.aiven.io/login`).

Stop and wait for user confirmation before continuing.

---

## Step 2: Choose Connection Mode

Use AskQuestion:

Prompt: `Where should the Share Group consumer connect?`
Options:
- `localhost (PLAINTEXT, localhost:9092)` (Recommended)
- `Aiven service (by name, SSL/mTLS)`

### 2.1 Localhost mode

Run:

```bash
bash scripts/setup_localhost.sh
source env.sh
```

This exports:
- `BOOTSTRAP_SERVERS=localhost:9092`
- `SHARE_GROUP_ID=product-photo-share-group`
- `TEMPLATE_JAVA_VARIANT=share_consumer_java_local`

The script also prints a verification checklist for broker-side
`group.coordinator.rebalance.protocols`.

The localhost broker must have:

```properties
group.coordinator.rebalance.protocols=classic,consumer,share
```

The skill does not modify localhost broker config; it only validates/instructs.

### 2.2 Aiven service mode

Ask for the service name if missing, then run:

```bash
bash scripts/setup_aiven_share.sh <service-name>
source env.sh
```

This script:
- Verifies `avn` login
- Downloads certificates to `cert/`
- Builds PKCS12 keystore + truststore with `keytool`
- Sets service config:
  `kafka.group_coordinator_rebalance_protocols=classic,consumer,share`
- Creates topic `product_photo_raw` if needed
- Writes connection variables to `env.sh`, including
  `TEMPLATE_JAVA_VARIANT=share_consumer_java_remote`

> **CRITICAL**: Do not print `env.sh` to the agent context because it includes
> generated store passwords. Use `source env.sh` and validate by variable length
> where needed.

---

## Step 3: Warm Up Topic, Then Run Consumer and Main Producer

### 3.1 Copy common Java template + selected provider classes to workspace root

```bash
cp -r <SKILL_DIR>/scripts/ <WORKSPACE_ROOT>/scripts/
mkdir -p <WORKSPACE_ROOT>/projects
PROJECT_DIR="<WORKSPACE_ROOT>/projects/share_consumer_java_$(date +%Y%m%d_%H%M%S)"
cp -r <SKILL_DIR>/templates/share_consumer_java "$PROJECT_DIR"
cp <SKILL_DIR>/templates/"$TEMPLATE_JAVA_VARIANT"/ProducerPropertiesProvider.java "$PROJECT_DIR"/
cp <SKILL_DIR>/templates/"$TEMPLATE_JAVA_VARIANT"/ConsumerPropertiesProvider.java "$PROJECT_DIR"/
printf '\nexport SHARE_GROUP_PROJECT_DIR="%s"\n' "$PROJECT_DIR" >> env.sh
source env.sh
```

Then verify:

```bash
ls "$SHARE_GROUP_PROJECT_DIR"/pom.xml
ls "$SHARE_GROUP_PROJECT_DIR"/SampleProducer.java
ls "$SHARE_GROUP_PROJECT_DIR"/ShareConsumer.java
ls "$SHARE_GROUP_PROJECT_DIR"/ProducerPropertiesProvider.java
ls "$SHARE_GROUP_PROJECT_DIR"/ConsumerPropertiesProvider.java
ls scripts/probe_topic.sh scripts/seed_topic.sh scripts/run_share_consumer.sh
```

### 3.2 Warm up topic before creating consumer

Run:

```bash
bash scripts/probe_topic.sh 3
```

This sends a few probe messages so `product_photo_raw` exists before the
consumer starts. Keep this count low; these are only pre-flight records.

### 3.3 Build and run the Share Group consumer (Terminal A)

Run:

```bash
bash scripts/run_share_consumer.sh
```

Keep this process running.

### 3.4 Seed sample metadata with main producer (Terminal B)

Run:

```bash
bash scripts/seed_topic.sh
```

This publishes JSON payloads (no keys) to `product_photo_raw`.
Run this only after Step 3.3 has started, because share-group consumers
effectively begin from latest messages.

The consumer app uses:
- `share.acknowledgement.mode=implicit`
- `max.poll.records=200`
- `deliveryCount()` inspection to log redelivered records
- `commitSync()` to advance implicit acknowledgements

Connection-specific producer/consumer options are selected via:
- `templates/share_consumer_java_local/{ProducerPropertiesProvider,ConsumerPropertiesProvider}.java` (PLAINTEXT)
- `templates/share_consumer_java_remote/{ProducerPropertiesProvider,ConsumerPropertiesProvider}.java` (SSL/mTLS)

---

## Step 4: Teardown Info

Do not run teardown automatically.

For Aiven service users, share:

```bash
# Optional: delete the demo topic from the existing service
avn service topic-delete "$KAFKA_SERVICE" product_photo_raw
```

For localhost users, share:

```bash
# Optional: delete the demo topic from local Kafka
kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic product_photo_raw
```

For troubleshooting and command reference, see [reference.md](reference.md).
