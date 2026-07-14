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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads image metadata from product_photo_raw using Kafka Share Groups. */
public final class ShareConsumer {
  private static final Logger logger = LoggerFactory.getLogger(ShareConsumer.class);
  private static final String TOPIC = "product_photo_raw";
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
  private static final AtomicBoolean running = new AtomicBoolean(true);

  private ShareConsumer() {}

  /** Runs a share-group consumer that logs records. */
  public static void main(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));
    try {
      Properties props = new ConsumerPropertiesProvider().properties();
      String shareGroup = props.getProperty("group.id");
      runConsumerLoop(props, shareGroup);
    } catch (IllegalStateException e) {
      logger.error("Invalid configuration for share consumer", e);
      System.exit(1);
    }
  }

  private static void runConsumerLoop(Properties props, String shareGroup) {
    try (KafkaShareConsumer<String, String> consumer = new KafkaShareConsumer<>(props)) {
      consumer.subscribe(List.of(TOPIC));
      logger.info("Subscribed to topic '{}' with share group '{}'.", TOPIC, shareGroup);

      while (running.get()) {
        try {
          ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
          for (ConsumerRecord<String, String> record : records) {
            // Individual record delivery monitoring tooling is limited, so it is useful to log/monitor it here.
            logRedeliveryIfNeeded(record);
            processRecord(record);
          }

          // Reason: In implicit mode, commitSync() advances acknowledgement for records
          // returned by the latest poll() call without per-record acknowledge().
          consumer.commitSync();
        } catch (RetriableException e) {
          logger.warn("Retriable Kafka error, continue polling", e);
        }
      }
    } catch (SerializationException e) {
      // Reason: Serialization failures can poison progress in share-group workflows;
      // fail fast so the operator can inspect payload/schema compatibility.
      logger.error("Serialization error while consuming shared records", e);
      throw e;
    } finally {
      MessageRecorder.close(); // VERIFICATION - remove together with recorder helper below
    }
  }

  private static void logRedeliveryIfNeeded(ConsumerRecord<String, String> record) {
    short deliveryCount = record.deliveryCount().orElse((short) 1);
    if (deliveryCount > 1) {
      logger.warn("Redelivered record observed (deliveryCount={}): {}", deliveryCount, record.value());
    }
  }

  private static void processRecord(ConsumerRecord<String, String> record) {
    // Template placeholder: implement business processing logic here.
    logger.info("Consumed: {}", record.value());
    MessageRecorder.record(record); // VERIFICATION - remove this line together with the class below
  }

  private static final class MessageRecorder {
    private static final PrintWriter writer = openWriter();

    private static PrintWriter openWriter() {
      String path = System.getenv("RECORD_OUTPUT_FILE");
      if (path == null || path.isBlank()) {
        path = "consumed_messages.txt";
      }
      try {
        return new PrintWriter(new FileWriter(path, true));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    static void record(ConsumerRecord<String, String> record) {
      writer.printf(
          "partition=%d offset=%d key=%s value=%s%n",
          record.partition(), record.offset(), record.key(), record.value());
      writer.flush();
    }

    static void close() {
      writer.close();
    }
  }
}
