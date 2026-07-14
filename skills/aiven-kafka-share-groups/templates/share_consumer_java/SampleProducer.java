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

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Seeds product_photo_raw with simple JSON image metadata records. */
public final class SampleProducer {
  private static final Logger logger = LoggerFactory.getLogger(SampleProducer.class);
  private static final String TOPIC = "product_photo_raw";

  private SampleProducer() {}

  /** Produces JSON metadata records (default 20) so share consumers have data to read. */
  public static void main(String[] args) {
    int messageCount = args.length > 0 ? Integer.parseInt(args[0]) : 20;
    Properties props = new ProducerPropertiesProvider().properties();

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      for (int i = 1; i <= messageCount; i++) {
        String payload = buildPayload(i);
        producer.send(new ProducerRecord<>(TOPIC, null, payload));
        logger.info("Produced {} -> {}", i, payload);
      }
      producer.flush();
      logger.info("Done. Produced {} messages to topic '{}'.", messageCount, TOPIC);
    }
  }

  private static String buildPayload(int index) {
    return String.format(
        "{\"image_id\":\"img-%04d\",\"filename\":\"product_%04d.jpg\","
            + "\"size_bytes\":%d,\"uploaded_at\":\"%s\",\"checksum\":\"%s\"}",
        index, index, 10000 + (index * 37), Instant.now(), UUID.randomUUID());
  }
}
