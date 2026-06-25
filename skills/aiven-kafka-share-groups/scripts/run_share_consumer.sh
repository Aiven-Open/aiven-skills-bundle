#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

JAVA_DIR="${SHARE_GROUP_PROJECT_DIR:-}"
JAR_PATH="$JAVA_DIR/target/kafka-share-groups-demo-1.0-SNAPSHOT-consumer.jar"

for var in BOOTSTRAP_SERVERS; do
  if [ -z "${!var:-}" ]; then
    echo "ERROR: $var is not set. Run setup script and source env.sh first." >&2
    exit 1
  fi
done

if [ -z "$JAVA_DIR" ]; then
  echo "ERROR: SHARE_GROUP_PROJECT_DIR is not set." >&2
  echo "Run skill Step 3.1 and source env.sh first." >&2
  exit 1
fi

if [ ! -f "$JAVA_DIR/pom.xml" ]; then
  echo "ERROR: Java project not found at '$JAVA_DIR'." >&2
  echo "Set SHARE_GROUP_PROJECT_DIR in env.sh or copy a template project first." >&2
  exit 1
fi

if [ ! -f "$JAVA_DIR/ConsumerPropertiesProvider.java" ]; then
  echo "ERROR: Consumer properties provider is missing at '$JAVA_DIR/ConsumerPropertiesProvider.java'." >&2
  echo "Copy provider classes from share_consumer_java_local or share_consumer_java_remote." >&2
  exit 1
fi

if [ ! -f "$JAVA_DIR/ProducerPropertiesProvider.java" ]; then
  echo "ERROR: Producer properties provider is missing at '$JAVA_DIR/ProducerPropertiesProvider.java'." >&2
  echo "Copy provider classes from share_consumer_java_local or share_consumer_java_remote." >&2
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  mkdir -p "$JAVA_DIR/src/main/java/com/aiven/demo"
  cp "$JAVA_DIR/SampleProducer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
  cp "$JAVA_DIR/ShareConsumer.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
  cp "$JAVA_DIR/ProducerPropertiesProvider.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
  cp "$JAVA_DIR/ConsumerPropertiesProvider.java" "$JAVA_DIR/src/main/java/com/aiven/demo/"
  echo "==> Building Java templates..."
  mvn -f "$JAVA_DIR/pom.xml" -q package -DskipTests
fi

echo "==> Running Kafka Share Group consumer..."
java -jar "$JAR_PATH"
