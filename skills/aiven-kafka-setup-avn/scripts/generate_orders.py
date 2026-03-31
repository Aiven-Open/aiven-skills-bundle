#!/usr/bin/env python3
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
import csv
import random
import sys

PRODUCTS = [
    ("dress", 15.90),
    ("jacket", 49.99),
    ("sneakers", 89.50),
    ("hat", 12.00),
    ("scarf", 22.75),
    ("jeans", 39.99),
    ("t-shirt", 9.99),
    ("boots", 110.00),
    ("sunglasses", 25.50),
    ("backpack", 55.00),
]


def main():
    output = sys.argv[1] if len(sys.argv) > 1 else "orders.csv"
    random.seed(42)

    with open(output, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "user_id", "product", "price"])
        for i in range(1, 21):
            product, base_price = random.choice(PRODUCTS)
            price = round(base_price + random.uniform(-5, 5), 2)
            user_id = random.randint(100, 999)
            writer.writerow([i, user_id, product, f"{price:.2f}"])

    print(f"Generated {output} with 20 rows.")


if __name__ == "__main__":
    main()
