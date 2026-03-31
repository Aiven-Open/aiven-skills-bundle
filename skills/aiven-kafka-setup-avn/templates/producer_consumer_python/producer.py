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
from dataclasses import asdict
from typing import Optional

from confluent_kafka import KafkaError, KafkaException, Message, Producer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import (
    MessageField,
    SerializationContext,
    SerializationError,
)

from order import Order

TOPIC = "orders"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)


class OrderProducer:
    """Reads orders from a CSV file and publishes them to the Kafka ``orders`` topic.

    Use as a context manager — the underlying Kafka producer is created on
    ``__enter__`` and flushed on ``__exit__``::

        with OrderProducer(...) as producer:
            producer.produce(csv_path)
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
        self._producer: Optional[Producer] = None
        self._serializer: Optional[AvroSerializer] = None

    def __enter__(self) -> "OrderProducer":
        """Create the Kafka producer and Avro serializer."""
        sr_client = SchemaRegistryClient(self._schema_registry_config())
        self._serializer = AvroSerializer(
            sr_client,
            to_dict=lambda order, ctx: asdict(order),
            conf={"auto.register.schemas": False, "use.latest.version": True},
        )
        self._producer = Producer(self._producer_config())
        return self

    def __exit__(self, *args: object) -> None:
        """Flush all buffered records before releasing the producer."""
        if self._producer is not None:
            self._producer.flush()

    def _schema_registry_config(self) -> dict:
        # Schema Registry endpoint uses a public Let's Encrypt cert, not
        # the Aiven internal CA (cert/ca.pem). Omitting ssl.ca.location lets
        # confluent_kafka fall back to certifi's public root bundle — correct here.
        # ssl.ca.location is only needed for the broker SASL_SSL connection below.
        return {
            "url": self.schema_registry_url,
            "basic.auth.user.info": f"{self.username}:{self.password}",
        }

    def _producer_config(self) -> dict:
        return {
            "bootstrap.servers": self.bootstrap_servers,
            "security.protocol": "SASL_SSL",
            # Disable TLS hostname verification — Aiven certificates use the
            # service hostname, not the individual broker hostname.
            "ssl.endpoint.identification.algorithm": "none",
            "sasl.mechanism": "SCRAM-SHA-256",
            "sasl.username": self.username,
            "sasl.password": self.password,
            "ssl.ca.location": self.ca_location,
            **self._performance_config(),
        }

    def _performance_config(self) -> dict:
        return {
            # Max bytes per batch per partition (default 16 KB). Larger = better
            # throughput at the cost of slightly higher latency.
            # https://kafka.apache.org/42/configuration/producer-configs/#producerconfigs_batch.size
            "batch.size": 12288,
            # Delay before sending a partial batch (default 0 ms). Allows more
            # records to accumulate in the buffer before dispatch to the broker.
            # https://kafka.apache.org/42/configuration/producer-configs/#producerconfigs_linger.ms
            "linger.ms": 5,
        }

    @staticmethod
    def _delivery_report(err: Optional[KafkaError], msg: Message) -> None:
        """Async delivery callback invoked by librdkafka for each produced record."""
        if err:
            logger.error("Delivery failed: %s", err)
        else:
            logger.info("Produced: partition=%d offset=%d", msg.partition(), msg.offset())

    def stop(self) -> None:
        """Signal the produce loop to exit after the current record."""
        logger.info("Shutdown signal received — stopping producer.")
        self.running = False

    def produce(self, csv_path: str) -> None:
        """Read orders from ``csv_path`` and send each one to the topic."""
        try:
            with open(csv_path, newline="") as f:
                reader = csv.DictReader(f)
                count = 0

                for row in reader:
                    if not self.running:
                        break
                    order = Order.from_csv(row)
                    # Async send — records are batched per batch.size / linger.ms
                    # before being dispatched to the broker.
                    self._producer.produce(
                        topic=TOPIC,
                        key=str(order.id),
                        value=self._serializer(
                            order,
                            SerializationContext(TOPIC, MessageField.VALUE),
                        ),
                        on_delivery=self._delivery_report,
                    )
                    logger.info("Sent: %s", order)
                    count += 1

            logger.info("Done. Produced %d messages.", count)

        except SerializationError as e:
            logger.error("Serialization failed — producer stopped: %s", e)
            raise
        except BufferError as e:
            logger.error("Producer queue full — tune buffering config: %s", e)
            raise
        except KafkaException as e:
            if e.args[0].retriable():
                logger.warning("Retriable Kafka error in producer: %s", e)
            else:
                logger.error("Fatal Kafka error in producer: %s", e)
            raise
        except Exception as e:
            logger.error("Unexpected error in producer: %s", e)
            raise


def main() -> None:
    parser = argparse.ArgumentParser(description="Produce orders from a CSV file to Kafka.")
    parser.add_argument("input", nargs="?", default="orders.csv", help="Input CSV path")
    args = parser.parse_args()
    csv_path = args.input

    with OrderProducer(
        bootstrap_servers=f"{os.environ['KAFKA_HOST']}:{os.environ['KAFKA_PORT']}",
        schema_registry_url=os.environ["SCHEMA_REGISTRY_URL"],
        username="producer-user",
        password=os.environ["PRODUCER_PASSWORD"],
        ca_location="cert/ca.pem",
    ) as order_producer:
        signal.signal(signal.SIGTERM, lambda _s, _f: order_producer.stop())
        signal.signal(signal.SIGINT, lambda _s, _f: order_producer.stop())
        order_producer.produce(csv_path)


if __name__ == "__main__":
    main()
