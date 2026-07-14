/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aiven.demo;

import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

final class ProducerPropertiesProvider {
  Properties properties() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env("BOOTSTRAP_SERVERS"));
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put("security.protocol", "SSL");
    props.put("ssl.endpoint.identification.algorithm", "");
    props.put("ssl.keystore.type", "PKCS12");
    props.put("ssl.keystore.location", env("SSL_KEYSTORE_PATH"));
    props.put("ssl.keystore.password", env("SSL_KEYSTORE_PASSWORD"));
    props.put("ssl.key.password", env("SSL_KEY_PASSWORD"));
    props.put("ssl.truststore.type", "PKCS12");
    props.put("ssl.truststore.location", env("SSL_TRUSTSTORE_PATH"));
    props.put("ssl.truststore.password", env("SSL_TRUSTSTORE_PASSWORD"));
    return props;
  }

  private static String env(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + name);
    }
    return value;
  }
}
