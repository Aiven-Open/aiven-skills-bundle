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

import argparse
import csv
import logging
import os
import signal
from datetime import datetime, timezone
from typing import IO, List, Optional

from confluent_kafka import Consumer, KafkaException
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer
from confluent_kafka.serialization import (
    MessageField,
    SerializationContext,
    SerializationError,
)

from order import Order

TOPIC = "orders"

# Reason: 1 second balances responsiveness with network overhead on a managed
# cloud service. Shorter polls increase CPU usage; longer polls delay completion.
POLL_TIMEOUT = 1.0

DEFAULT_EXPECTED_MESSAGES = 20

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)


class OrderConsumer:
    """Consumes orders from the Kafka ``orders`` topic and writes them to a CSV.

    Use as a context manager — the underlying Kafka consumer is created on
    ``__enter__`` and closed on ``__exit__``::

        with OrderConsumer(...) as consumer:
            consumer.consume(output_path)
    """

    def __init__(
        self,
        bootstrap_servers: str,
        schema_registry_url: str,
        username: str,
        password: str,
        ca_location: str,
    ) -> None:
        self.bootstrap_servers = bootstrap_servers
        self.schema_registry_url = schema_registry_url
        self.username = username
        self.password = password
        self.ca_location = ca_location
        self.running = True
        self._consumer: Optional[Consumer] = None
        self._deserializer: Optional[AvroDeserializer] = None

    def __enter__(self) -> "OrderConsumer":
        """Create the Kafka consumer and Avro deserializer."""
        sr_client = SchemaRegistryClient(self._schema_registry_config())
        self._deserializer = AvroDeserializer(sr_client)
        self._consumer = Consumer(self._consumer_config())
        self._consumer.subscribe([TOPIC])
        return self

    def __exit__(self, *args: object) -> None:
        if self._consumer is not None:
            self._consumer.close()

    def _schema_registry_config(self) -> dict:
        # Schema Registry endpoint uses a public Let's Encrypt cert, not
        # the Aiven internal CA (cert/ca.pem). Omitting ssl.ca.location lets
        # confluent_kafka fall back to certifi's public root bundle — correct here.
        # ssl.ca.location is only needed for the broker SASL_SSL connection below.
        return {
            "url": self.schema_registry_url,
            "basic.auth.user.info": f"{self.username}:{self.password}",
        }

    def _consumer_config(self) -> dict:
        return {
            "bootstrap.servers": self.bootstrap_servers,
            "security.protocol": "SASL_SSL",
            # Disable TLS hostname verification — Aiven certificates use the
            # service hostname, not the individual broker hostname.
            # librdkafka uses "none" (not "") to disable; empty string is invalid.
            "ssl.endpoint.identification.algorithm": "none",
            "sasl.mechanism": "SCRAM-SHA-256",
            "sasl.username": self.username,
            "sasl.password": self.password,
            "ssl.ca.location": self.ca_location,
            "group.id": "orders-consumer-group",
            # Static group membership: broker skips rebalance on restart if the
            # instance ID is unchanged, reassigning the same partitions immediately.
            "group.instance.id": "consumer-1",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
            **self._performance_config(),
        }

    def _performance_config(self) -> dict:
        return {
            # Min bytes broker accumulates before responding to a fetch (default 1).
            # Higher = fewer round-trips.
            # https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_fetch.min.bytes
            "fetch.min.bytes": 1,
            # Max time broker waits if fetch.min.bytes not reached (default 500 ms).
            # https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_fetch.max.wait.ms
            "fetch.wait.max.ms": 500,
            # Max time between poll() calls before the broker triggers a rebalance.
            # Tune alongside session.timeout.ms when processing is slow.
            # https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_max.poll.interval.ms
            "max.poll.interval.ms": 300000,
            # Note: large record batches could lead to session timeouts.
            # If session.timeout.ms is tuned, heartbeat.interval.ms must be tuned
            # as well (keep it ≤ 1/3 of session.timeout.ms).
            "session.timeout.ms": 45000,
            "heartbeat.interval.ms": 15000,
        }

    def stop(self) -> None:
        """Signal the consume loop to exit after the current poll completes."""
        logger.info("Shutdown signal received — stopping consumer.")
        self.running = False

    def consume(
        self,
        output_path: str,
        expected_messages: int = DEFAULT_EXPECTED_MESSAGES,
    ) -> None:
        """Fetch messages in batches until ``expected_messages`` are received and write to CSV."""
        try:
            with open(output_path, "w", newline="") as f:
                writer = csv.writer(f)
                writer.writerow(["id", "user_id", "product", "price", "completed_at"])

                received = 0

                while self.running:
                    msgs = self._consumer.consume(num_messages=10, timeout=POLL_TIMEOUT)

                    if not msgs:
                        continue

                    received += self._write_records(msgs, writer, f)
                    # Reason: commit after the batch is flushed to disk so offsets
                    # are never ahead of persisted data (at-least-once guarantee).
                    self._consumer.commit(asynchronous=False)

                    if received >= expected_messages:
                        break

                f.flush()

            logger.info(
                "Shutting down. Consumed %d messages to %s.", received, output_path
            )

        except SerializationError as e:
            logger.error("Deserialization failed — check schema compatibility: %s", e)
            raise
        except KafkaException as e:
            if e.args[0].retriable():
                logger.warning("Retriable Kafka error in consumer: %s", e)
            else:
                logger.error("Fatal Kafka error in consumer: %s", e)
            raise
        except Exception as e:
            logger.error("Unexpected error in consumer: %s", e)
            raise

    def _write_records(
        self,
        msgs: List[object],
        writer: csv.writer,
        f: IO[str],
    ) -> int:
        """Deserialize a batch of messages, write each as a CSV row, and flush the file.
        """
        count = 0
        for msg in msgs:
            if msg.error():
                logger.error("Consumer error: %s", msg.error())
                continue
            order = Order.from_avro(
                self._deserializer(
                    msg.value(),
                    SerializationContext(TOPIC, MessageField.VALUE),
                )
            )
            completed_at = datetime.now(timezone.utc).strftime("%d-%m-%YZ%H:%M:%S")
            row = [order.id, order.user_id, order.product, order.price, completed_at]
            writer.writerow(row)
            count += 1
            logger.info("Consumed: %s", row)
        f.flush()
        return count


def main() -> None:
    parser = argparse.ArgumentParser(description="Consume orders from Kafka and write to CSV.")
    parser.add_argument("output", nargs="?", default="orders_completed.csv", help="Output CSV path")
    parser.add_argument(
        "--max-messages",
        type=int,
        default=DEFAULT_EXPECTED_MESSAGES,
        help="Stop after consuming this many messages (default: %(default)s)",
    )
    args = parser.parse_args()

    with OrderConsumer(
        bootstrap_servers=f"{os.environ['KAFKA_HOST']}:{os.environ['KAFKA_PORT']}",
        schema_registry_url=os.environ["SCHEMA_REGISTRY_URL"],
        username="consumer-user",
        password=os.environ["CONSUMER_PASSWORD"],
        ca_location="cert/ca.pem",
    ) as order_consumer:
        signal.signal(signal.SIGTERM, lambda _s, _f: order_consumer.stop())
        signal.signal(signal.SIGINT, lambda _s, _f: order_consumer.stop())
        order_consumer.consume(args.output, expected_messages=args.max_messages)


if __name__ == "__main__":
    main()
