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

LANG="${1:-java}"
CSV_PATH="${2:-orders.csv}"

for var in KAFKA_HOST KAFKA_PORT SCHEMA_REGISTRY_URL PRODUCER_PASSWORD TRUSTSTORE_PASSWORD TRUSTSTORE_PATH; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: $var is not set. Export it first."
        exit 1
    fi
done

if [ ! -f "$CSV_PATH" ]; then
    echo "ERROR: $CSV_PATH not found. Run: python3 scripts/generate_orders.py"
    exit 1
fi

case "$LANG" in
    java)
        echo "==> Compiling Java producer..."
        JAVA_DIR="templates/producer_consumer_java"
        mkdir -p "$JAVA_DIR/src/main/java/com/aiven/demo"
        cp "$JAVA_DIR/Producer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
        cp "$JAVA_DIR/Consumer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
        cp "$JAVA_DIR/Order.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
        mvn -f "$JAVA_DIR/pom.xml" -q package -DskipTests
        echo "==> Running Java producer..."
        java -cp "$JAVA_DIR/target/kafka-orders-demo-1.0-SNAPSHOT.jar" \
            com.aiven.demo.Producer "$CSV_PATH"
        ;;
    python)
        echo "==> Running Python producer..."
        python3 templates/producer_consumer_python/producer.py "$CSV_PATH"
        ;;
    *)
        echo "ERROR: Unsupported language '$LANG'. Use 'java' or 'python'."
        exit 1
        ;;
esac
