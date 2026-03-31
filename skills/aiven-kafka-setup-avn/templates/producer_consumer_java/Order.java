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

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/** Represents a single order record read from CSV and serialised to Avro. */
public final class Order {

  final int id;
  final int userId;
  final String product;
  final double price;

  Order(int id, int userId, String product, double price) {
    this.id = id;
    this.userId = userId;
    this.product = product;
    this.price = price;
  }

  static Order fromCsv(String csvLine) {
    String[] parts = csvLine.split(",");
    return new Order(
        Integer.parseInt(parts[0]),
        Integer.parseInt(parts[1]),
        parts[2],
        Double.parseDouble(parts[3]));
  }

  GenericRecord toAvro(Schema schema) {
    GenericRecord record = new GenericData.Record(schema);
    record.put("id", id);
    record.put("user_id", userId);
    record.put("product", product);
    record.put("price", price);
    return record;
  }
}
