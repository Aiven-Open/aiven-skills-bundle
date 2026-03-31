/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aiven.demo;

// Note: Confluent Serdes is used here, but alternatives like Apicurio Serdes exist.
// Apicurio provides compatibility with Confluent's API but has some limitations
// (e.g., lacks support for schemas with references when using the compatible API).
// See: https://croz.net/serde-libraries-comparison/
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consumes orders from the Kafka {@code orders} topic and writes them to a CSV file. */
public final class Consumer {

  private static final Logger logger = LoggerFactory.getLogger(Consumer.class);
  private static final String TOPIC = "orders";
  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("dd-MM-yyyy'Z'HH:mm:ss");

  // Reason: 1 second balances responsiveness with network overhead on a managed cloud service.
  // Shorter polls increase CPU usage without benefit; longer polls delay completion detection.
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
  private static final int EXPECTED_RECORDS = 20;

  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Consumes orders and writes them to the given output file
   * (default: {@code orders_completed.csv}).
   */
  public static void main(String[] args) throws IOException {
    String bootstrapServers = System.getenv("KAFKA_HOST") + ":" + System.getenv("KAFKA_PORT");
    String schemaRegistryUrl = System.getenv("SCHEMA_REGISTRY_URL");
    String username = "consumer-user";
    String password = System.getenv("CONSUMER_PASSWORD");
    String truststorePath = System.getenv("TRUSTSTORE_PATH");
    String truststorePassword = System.getenv("TRUSTSTORE_PASSWORD");
    String outputPath = args.length > 0 ? args[0] : "orders_completed.csv";

    // Add a graceful shutdown hook to stop the consumer when the JVM exits.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));

    String instanceId = "consumer-1";
    Properties props =
        buildConsumerProperties(
            bootstrapServers, schemaRegistryUrl, username, password,
            truststorePath, truststorePassword, instanceId);
    applyPerformanceTuning(props);

    // consumer.close() is called automatically by try-with-resources.
    try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(props);
        PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {

      consumer.subscribe(Collections.singletonList(TOPIC));
      writer.println("id,user_id,product,price,completed_at");

      int received = 0;

      while (running.get()) {
        try {
          ConsumerRecords<String, GenericRecord> records = consumer.poll(POLL_TIMEOUT);
          if (!records.isEmpty()) {
            received += writeRecords(records, writer);
          }
        } catch (WakeupException e) {
          // Triggered by consumer.wakeup() from another thread — clean exit.
          logger.info("Consumer wakeup — shutting down.");
          break;
        } catch (CommitFailedException e) {
          // Processing exceeded max.poll.interval.ms; broker triggered a rebalance.
          // Tune max.poll.interval.ms or reduce per-poll work to fix.
          logger.warn("Offset commit failed — processing too slow, retrying", e);
          continue;
        }
        if (received >= EXPECTED_RECORDS) {
          break;
        }
      }

      logger.info("Shutting down. Consumed {} messages to {}.", received, outputPath);
    } catch (SerializationException e) {
      // Fatal: schema mismatch between producer and consumer. Verify schema in the registry.
      logger.error("Deserialization failed — check schema compatibility", e);
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error in consumer", e);
      throw e;
    }
  }

  private static Properties buildConsumerProperties(
      String bootstrapServers,
      String schemaRegistryUrl,
      String username,
      String password,
      String truststorePath,
      String truststorePassword,
      String instanceId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "orders-consumer-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    // Static group membership — if the process restarts with the same instance ID,
    // the broker skips rebalance and reassigns the same partitions immediately.
    props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, instanceId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
    props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    props.put("basic.auth.credentials.source", "USER_INFO");
    props.put("basic.auth.user.info", username + ":" + password);
    props.put("security.protocol", "SASL_SSL");
    props.put("ssl.endpoint.identification.algorithm", "");
    props.put("sasl.mechanism", "SCRAM-SHA-256");
    props.put(
        "sasl.jaas.config",
        "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"" + username + "\" password=\"" + password + "\";");
    // Note: keystore config is not required for SASL_SSL
    props.put("ssl.truststore.type", "PKCS12");
    props.put("ssl.truststore.location", truststorePath);
    props.put("ssl.truststore.password", truststorePassword);
    return props;
  }

  private static void applyPerformanceTuning(Properties props) {
    // Min bytes broker accumulates before responding to a fetch (default 1). Higher = fewer
    // round-trips.
    // https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_fetch.min.bytes
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
    // Max time broker waits if fetch.min.bytes not reached (default 500ms).
    // https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_fetch.max.wait.ms
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
    // Max time between poll() calls before the broker considers the consumer dead and rebalances.
    // Tune this alongside session.timeout.ms if processing is slow (to avoid CommitFailedException).
    // https://kafka.apache.org/42/configuration/consumer-configs/#consumerconfigs_max.poll.interval.ms
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

    // Note: A large amount of records to process could lead to session timeouts.
    // If we tune session timeouts, we should tune heartbeat intervals as well.
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15000);
  }

  private static int writeRecords(
      ConsumerRecords<String, GenericRecord> records, PrintWriter writer) {
    int count = 0;
    for (var record : records) {
      GenericRecord order = record.value();
      String completedAt = LocalDateTime.now().format(FMT);
      String line =
          String.format(
              "%s,%s,%s,%s,%s",
              order.get("id"), order.get("user_id"),
              order.get("product"), order.get("price"), completedAt);
      writer.println(line);
      logger.info("Consumed: {}", line);
      count++;
      if (count % 100 == 0) {
        writer.flush();
      }
    }
    writer.flush();
    return count;
  }
}
