#!/usr/bin/env bash
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

set -euo pipefail

KAFKA_SERVICE="${1:?Usage: $0 <service-name>}"

echo "==> This will DELETE the Aiven service '$KAFKA_SERVICE' and local files."
read -rp "Are you sure? (y/N): " confirm
if [[ "$confirm" != [yY] ]]; then
    echo "Aborted."
    exit 0
fi

echo "==> Terminating Aiven service: $KAFKA_SERVICE"
avn service terminate "$KAFKA_SERVICE" --force || echo "WARN: Service deletion failed (may already be deleted)."

echo "==> Removing local cert/ directory..."
rm -rf cert/

echo "==> Removing generated CSV files and env.sh..."
rm -f orders.csv orders_completed.csv env.sh

echo "==> Removing Java build artifacts..."
rm -rf templates/producer_consumer_java/target/
rm -rf templates/producer_consumer_java/src/

echo "==> Teardown complete."
