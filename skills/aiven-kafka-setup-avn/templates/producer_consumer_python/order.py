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

from dataclasses import dataclass


@dataclass
class Order:
    """Represents a single order record exchanged via the Kafka ``orders`` topic.

    Provides typed fields and factory class methods for both CSV ingestion (producer side) and Avro
    deserialization (consumer side).
    """

    id: int
    user_id: int
    product: str
    price: float

    @classmethod
    def from_csv(cls, row: dict) -> "Order":
        """Parse a ``csv.DictReader`` row into a typed Order instance.

        Explicit casts guard against the fact that DictReader yields every
        value as a string.
        """
        return cls(
            id=int(row["id"]),
            user_id=int(row["user_id"]),
            product=row["product"],
            price=float(row["price"]),
        )

    @classmethod
    def from_avro(cls, record: dict) -> "Order":
        """Parse an Avro-decoded dict into a typed Order instance.

        ``AvroDeserializer`` returns Avro records as plain dicts by default.
        Explicit casts normalise the types across schema evolution scenarios
        (e.g. Avro ``long`` arriving as ``int``).
        """
        return cls(
            id=int(record["id"]),
            user_id=int(record["user_id"]),
            product=str(record["product"]),
            price=float(record["price"]),
        )

