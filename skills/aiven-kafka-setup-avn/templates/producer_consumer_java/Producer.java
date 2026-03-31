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
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NotLeaderOrFollowerException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads orders from a CSV file and publishes them to the Kafka {@code orders} topic. */
public final class Producer {

  private static final Logger logger = LoggerFactory.getLogger(Producer.class);
  private static final String TOPIC = "orders";
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /** Reads orders from the given CSV file (default: {@code orders.csv}) and produces them. */
  public static void main(String[] args) throws IOException {
    String bootstrapServers = System.getenv("KAFKA_HOST") + ":" + System.getenv("KAFKA_PORT");
    String schemaRegistryUrl = System.getenv("SCHEMA_REGISTRY_URL");
    String username = "producer-user";
    String password = System.getenv("PRODUCER_PASSWORD");
    String truststorePath = System.getenv("TRUSTSTORE_PATH");
    String truststorePassword = System.getenv("TRUSTSTORE_PASSWORD");
    String csvPath = args.length > 0 ? args[0] : "orders.csv";

    // Add a graceful shutdown hook to stop the producer when the JVM exits.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));

    Schema schema =
        new Schema.Parser().parse(Files.readString(Path.of("templates/order.avsc")));

    Properties props =
        buildProducerProperties(
            bootstrapServers, schemaRegistryUrl, username, password,
            truststorePath, truststorePassword);
    applyPerformanceTuning(props);

    // AutoCloseable: producer.close() flushes buffered records before releasing resources.
    try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(props);
        BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {

      String header = reader.readLine(); // skip CSV header
      String line;
      int count = 0;

      while (running.get() && (line = reader.readLine()) != null) {
        try {
          Order order = Order.fromCsv(line);

          // Async send — records are batched per batch.size/linger.ms before going to broker.
          producer.send(
              new ProducerRecord<>(TOPIC, String.valueOf(order.id), order.toAvro(schema)));
          count++;
          logger.info("Produced: {}", line);
        } catch (NotLeaderOrFollowerException | TimeoutException e) {
          // Retriable: leader election in progress or broker timeout — skip record and continue.
          logger.warn("Retriable error — skipping record: {}", line, e);
          continue;
        }
      }

      producer.flush();
      logger.info("Done. Produced {} messages.", count);
    } catch (SerializationException e) {
      // Fatal: schema mismatch. Verify schema in the registry.
      logger.error("Serialization failed — producer stopped", e);
      throw e;
    } catch (RecordTooLargeException e) {
      // Fatal: payload exceeds message.max.bytes. Reduce record size.
      logger.error("Record too large — producer stopped", e);
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error in producer", e);
      throw e;
    }
  }

  private static Properties buildProducerProperties(
      String bootstrapServers,
      String schemaRegistryUrl,
      String username,
      String password,
      String truststorePath,
      String truststorePassword) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    // It is a good practice to disable auto-registration since the schema is created
    // in the setup_aiven_kafka script.
    props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, false);
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
    // Max bytes per batch per partition (default 16KB). Larger = better throughput.
    // https://kafka.apache.org/42/configuration/producer-configs/#producerconfigs_batch.size
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 12288);
    // Delay before sending a partial batch (default 0ms). Allows more records to accumulate.
    // https://kafka.apache.org/42/configuration/producer-configs/#producerconfigs_linger.ms
    props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
  }
}
