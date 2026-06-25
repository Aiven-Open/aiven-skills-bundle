# Kafka Share Groups Reference

Quick reference for the `aiven-kafka-share-groups` skill.

---

## Core API

- Consumer class: `org.apache.kafka.clients.consumer.KafkaShareConsumer`
- Record type: `ConsumerRecord<K, V>`
- Share-group delivery attempts: `record.deliveryCount()` returns `Optional<Short>`

---

## Required Broker Setting

Share groups require:

```properties
group.coordinator.rebalance.protocols=classic,consumer,share
```

### Aiven service

```bash
avn service update <SERVICE_NAME> \
  -c kafka.group_coordinator_rebalance_protocols=classic,consumer,share
avn service wait <SERVICE_NAME>
```

### Localhost broker

Set it in your Kafka broker config (`server.properties`) and restart broker.
The skill verifies and instructs; it does not modify localhost broker config.

---

## Acknowledgement Mode

`share.acknowledgement.mode=implicit` (default):
- No per-record `acknowledge()` calls
- Records are implicitly accepted on next `poll()` / `commitSync()`

Use explicit mode only when you need per-record acceptance/release behavior.

---

## Topic and Payload

- Topic: `product_photo_raw`
- Messages: plain JSON strings, no keys, no Schema Registry

Example payload:

```json
{
  "image_id": "img-0001",
  "filename": "product_0001.jpg",
  "size_bytes": 10037,
  "uploaded_at": "2026-06-25T10:15:30Z",
  "checksum": "f6b5fdf8-499f-4204-bf33-96fb857fead1"
}
```

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|--------------|-----|
| `UNKNOWN_MEMBER_ID` / share-group errors | Broker missing share rebalance protocol | Ensure `classic,consumer,share` is enabled |
| SSL handshake failure | Missing or wrong keystore/truststore env values | Re-run `setup_aiven_share.sh`, `source env.sh` |
| Consumer gets no records | Consumer started after main producer messages were published (share groups are latest-first) or wrong bootstrap address | Run `probe_topic.sh` first (topic warm-up), then start `run_share_consumer.sh`, then run `seed_topic.sh`; verify `BOOTSTRAP_SERVERS` |
| `avn user info` fails | Not logged in or token expired | `avn user login <email>` |

---

## Useful Commands

```bash
# Verify connection env
echo "$BOOTSTRAP_SERVERS"

# Run setup + warm-up + consumer + main producer (in this order)
bash scripts/setup_aiven_share.sh <SERVICE_NAME>
source env.sh
bash scripts/probe_topic.sh 3
bash scripts/run_share_consumer.sh
# in a second terminal/session:
bash scripts/seed_topic.sh
```
