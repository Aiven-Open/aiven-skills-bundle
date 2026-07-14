# Aiven Skills Bundle — Versions

Current versions of all skills. Agents can compare against local versions to check for updates.

| Skill | Version | Last Updated |
|-------|---------|--------------|
| aiven-kafka-setup-avn | 1.0.0 | 2026-04-02 |
| aiven-kafka-share-groups | 0.1.0 | 2026-07-13 |

## Recent Changes

### 2026-07-14

- Fixed `aiven-kafka-share-groups` logic in `setup_aiven_share.sh` to always regenerate the keystore from certificates, ensuring the generated `KEYSTORE_PASSWORD` matches the keystore file.

### 2026-07-13

- Added `aiven-kafka-share-groups` to the versions registry at `0.1.0`.
- Updated `aiven-kafka-share-groups` `allowed-tools` metadata to include tools used by its scripts (`openssl`, `mvn`, `java`, and `bash` script chaining).

### 2026-04-02

- Initial release of `aiven-kafka-setup-avn` skill: create and configure an Apache Kafka
  cluster on Aiven using the `avn` CLI, including SASL_SSL auth, Schema Registry, and a
  working producer/consumer example in Python and Java.
