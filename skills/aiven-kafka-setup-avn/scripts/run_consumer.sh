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
OUTPUT_PATH="${2:-orders_completed.csv}"

for var in KAFKA_HOST KAFKA_PORT SCHEMA_REGISTRY_URL CONSUMER_PASSWORD TRUSTSTORE_PASSWORD TRUSTSTORE_PATH; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: $var is not set. Export it first."
        exit 1
    fi
done

case "$LANG" in
    java)
        JAVA_DIR="templates/producer_consumer_java"
        JAR="$JAVA_DIR/target/kafka-orders-demo-1.0-SNAPSHOT.jar"
        if [ ! -f "$JAR" ]; then
            echo "==> Compiling Java consumer..."
            mkdir -p "$JAVA_DIR/src/main/java/com/aiven/demo"
            cp "$JAVA_DIR/Producer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
            cp "$JAVA_DIR/Consumer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
            cp "$JAVA_DIR/Order.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
            mvn -f "$JAVA_DIR/pom.xml" -q package -DskipTests
        fi
        echo "==> Running Java consumer..."
        java -cp "$JAR" com.aiven.demo.Consumer "$OUTPUT_PATH"
        ;;
    python)
        echo "==> Running Python consumer..."
        python3 templates/producer_consumer_python/consumer.py "$OUTPUT_PATH"
        ;;
    *)
        echo "ERROR: Unsupported language '$LANG'. Use 'java' or 'python'."
        exit 1
        ;;
esac
