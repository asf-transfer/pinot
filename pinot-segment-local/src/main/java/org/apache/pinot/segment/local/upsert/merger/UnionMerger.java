/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.segment.local.upsert.merger;

import java.util.Set;
import java.util.TreeSet;


public class UnionMerger implements PartialUpsertMerger {
  UnionMerger() {
  }

  /**
   * Append the new value from incoming row to the given multi-value field of previous record.
   */
  @Override
  public Object merge(Object previousValue, Object currentValue) {
    return union((Object[]) previousValue, (Object[]) currentValue);
  }

  private static Object union(Object[] a, Object[] b) {
    Set<Object> union = new TreeSet<>();
    for (int i = 0; i < a.length; i++) {
      union.add(a[i]);
    }
    for (int j = 0; j < b.length; j++) {
      union.add(b[j]);
    }
    return union.toArray();
  }
}