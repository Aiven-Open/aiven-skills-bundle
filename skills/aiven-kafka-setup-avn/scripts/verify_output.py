#!/usr/bin/env python3
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

import csv
import sys
import os

def main():
    output_file = sys.argv[1] if len(sys.argv) > 1 else "orders_completed.csv"
    expected_rows = 20
    expected_header = ["id", "user_id", "product", "price", "completed_at"]
    errors = 0

    print(f"==> Verifying {output_file}")

    if not os.path.isfile(output_file):
        print(f"FAIL: {output_file} does not exist.")
        sys.exit(1)

    with open(output_file, 'r', newline='') as f:
        reader = csv.reader(f)
        try:
            header = next(reader)
        except StopIteration:
            print("FAIL: File is empty.")
            sys.exit(1)

        if header != expected_header:
            print("FAIL: Header mismatch.")
            print(f"  Expected: {','.join(expected_header)}")
            print(f"  Got:      {','.join(header)}")
            errors += 1

        data_rows = 0
        preview_rows = []
        for i, row in enumerate(reader, start=1):
            data_rows += 1
            if len(preview_rows) < 5:
                preview_rows.append(row)
            
            if len(row) != len(expected_header):
                print(f"FAIL: Wrong column count on row {i}")
                errors += 1
                continue
            
            completed_at = row[4].strip()
            if not completed_at:
                print(f"FAIL: Missing completed_at on row {i}")
                errors += 1

        if data_rows != expected_rows:
            print(f"FAIL: Expected {expected_rows} data rows, got {data_rows}.")
            errors += 1

    if errors == 0:
        print(f"PASS: {output_file} has {data_rows} rows with correct structure.\n")
        print("Preview (first 5 rows):")
        print(",".join(header))
        for row in preview_rows:
            print(",".join(row))
    else:
        print(f"\nFAILED: {errors} issue(s) found.")
        sys.exit(1)

if __name__ == "__main__":
    main()
