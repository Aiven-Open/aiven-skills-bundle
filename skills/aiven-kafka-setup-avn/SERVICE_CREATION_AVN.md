# Kafka Service Creation (avn CLI)

How to create an Aiven for Apache Kafka service: choose a region and plan (interactive),
then run `scripts/setup_aiven_kafka.sh` which handles everything else automatically — service
creation, users, ACLs, schema registration, env-var extraction, and verification.

> **This file is referenced by SKILL.md Step 2.** Follow it exactly.

---

## 1. Choose a Region

If the user already provided a `CLOUD_NAME` in their request, use it directly and skip
the question. Otherwise, ask which region they want using the **AskQuestion tool** before
proceeding.

You **MUST** use the exact `CLOUD_NAME` from the table below:

| Region | CLOUD_NAME |
|--------|------------|
| Asia | `do-sgp` |
| Australia | `do-syd` |
| Canada | `do-tor` |
| Europe | `do-fra` |
| United States | `do-nyc` |

**CRITICAL**: Do NOT use any other cloud names or regions. If the user asks for a region
not in this list, inform them that only these specific regions are supported by this
skill's automated setup.

---

## 2. Choose a Plan

### 2.1 Plan exclusion rules

**NEVER recommend or use these plans:**

| Plan | Reason |
|------|--------|
| `free-0` | Does NOT support Schema Registry or SASL user management — incompatible with this skill's workflow |
| `startup-2` | **Deprecated** — not available on Kafka versions 3.9 and above. The API returns HTTP 400 if you try to create it |

### 2.2 Default plan selection

- If the user asks for the **cheapest** plan: select `startup-4` (the cheapest plan that supports Schema Registry and SASL).
- If the user does **not** specify a plan: default to `startup-4`.
- If the user explicitly names a valid plan (e.g. `business-4`): use it.
- **Always confirm** the selected plan and its hourly cost with the user before creating the service.

To look up pricing, run:

```bash
avn service plans --service-type kafka --cloud "$CLOUD_NAME"
```

---

## 3. Run the Setup Script

Once region, plan, and service name are decided, **one command** does everything:

```bash
bash scripts/setup_aiven_kafka.sh <service-name> <CLOUD_NAME> <plan> <kafka-version>
```

Example:

```bash
bash scripts/setup_aiven_kafka.sh my-kafka do-fra startup-4 4.1
```

**ALWAYS** use the latest Kafka version. As of 2026-03, the latest is **4.1**.

The script handles all of the following automatically:
1. Verifies `avn` login
2. Creates the Kafka service with SASL_SSL + Schema Registry enabled
3. Waits for the service to reach RUNNING state
4. Downloads TLS certificates to `cert/`
5. Creates `producer-user` and `consumer-user`
6. Creates the `orders` topic (3 partitions, replication factor 2)
7. Sets topic ACLs (producer=write, consumer=read)
8. Sets Schema Registry ACLs
9. Registers the Avro schema via the avn CLI
10. Extracts connection details (`KAFKA_HOST`, `KAFKA_PORT`, `SCHEMA_REGISTRY_URL`)
11. Extracts passwords (`AVNADMIN_PASS`, `PRODUCER_PASSWORD`, `CONSUMER_PASSWORD`)
12. Writes everything to **`env.sh`** and verifies all values are non-empty

> **Password safety**: The script never prints password values. It verifies
> passwords by length only and writes them directly to `env.sh`. The raw
> `avn service user-list` output never appears in agent context.

---

## 4. Load Environment Variables

After the script completes, load the variables into the current shell:

```bash
source env.sh
```

Verify they are set:

```bash
echo "KAFKA_HOST=$KAFKA_HOST"
echo "KAFKA_PORT=$KAFKA_PORT"
echo "SCHEMA_REGISTRY_URL=$SCHEMA_REGISTRY_URL"
echo "AVNADMIN_PASS length: ${#AVNADMIN_PASS}"
echo "PRODUCER_PASSWORD length: ${#PRODUCER_PASSWORD}"
echo "CONSUMER_PASSWORD length: ${#CONSUMER_PASSWORD}"
echo "TRUSTSTORE_PASSWORD length: ${#TRUSTSTORE_PASSWORD}"
```

> **CRITICAL**: Do NOT read or cat `env.sh` in the agent context — it contains
> passwords. Only `source` it and verify by length.

---

## Environment Variable Reference

After sourcing `env.sh`, these variables are available:

| Variable | Example value |
|----------|---------------|
| `KAFKA_SERVICE` | `my-kafka` |
| `KAFKA_HOST` | `my-kafka-proj.f.aivencloud.com` |
| `KAFKA_PORT` | `28620` |
| `SCHEMA_REGISTRY_URL` | `https://my-kafka-proj.f.aivencloud.com:28623` |
| `AVNADMIN_PASS` | *(never printed)* |
| `PRODUCER_PASSWORD` | *(never printed)* |
| `CONSUMER_PASSWORD` | *(never printed)* |
| `TRUSTSTORE_PASSWORD` | *(never printed)* |
