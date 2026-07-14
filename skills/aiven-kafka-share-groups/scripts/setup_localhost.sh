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

ENV_FILE="env.sh"

cat > "$ENV_FILE" <<'EOF'
export BOOTSTRAP_SERVERS="localhost:9092"
export SHARE_GROUP_ID="product-photo-share-group"
export TEMPLATE_JAVA_VARIANT="share_consumer_java_local"
EOF

echo "==> Wrote $ENV_FILE for localhost mode."
echo ""
echo "IMPORTANT: Share Groups require the broker to enable rebalance protocols:"
echo "  group.coordinator.rebalance.protocols=classic,consumer,share"
echo ""
echo "Verify your broker config before running the consumer."
