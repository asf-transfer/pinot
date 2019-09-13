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
package org.apache.pinot.core.data.table;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.commons.collections.CollectionUtils;
import org.apache.pinot.common.request.AggregationInfo;
import org.apache.pinot.common.request.SelectionSort;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.data.order.OrderByUtils;


/**
 * {@link Table} implementation for aggregating TableRecords based on combination of keys
 */
@NotThreadSafe
public class SimpleIndexedTable extends IndexedTable {

  private List<Record> _records;
  private Map<Key, Integer> _lookupTable;
  private Comparator<Record> _orderByComparator;

  @Override
  public void init(@Nonnull DataSchema dataSchema, List<AggregationInfo> aggregationInfos, List<SelectionSort> orderBy,
      int maxCapacity, boolean sort) {
    super.init(dataSchema, aggregationInfos, orderBy, maxCapacity, sort);

    _records = new ArrayList<>(maxCapacity);
    _lookupTable = new HashMap<>(maxCapacity);

    if (CollectionUtils.isNotEmpty(_orderBy)) {
      _orderByComparator = OrderByUtils.getKeysAndValuesComparator(_dataSchema, _orderBy, _aggregationInfos);
    }
  }

  /**
   * Non thread safe implementation of upsert to insert {@link Record} into the {@link Table}
   */
  @Override
  public boolean upsert(@Nonnull Record newRecord) {
    Key keys = newRecord.getKey();
    Preconditions.checkNotNull(keys, "Cannot upsert record with null keys");

    Integer index = _lookupTable.get(keys);
    if (index == null) {
      index = size();
      _lookupTable.put(keys, index);
      _records.add(index, newRecord);
    } else {
      Record existingRecord = _records.get(index);
      for (int i = 0; i < _aggregationFunctions.size(); i++) {
        existingRecord.getValues()[i] =
            _aggregationFunctions.get(i).merge(existingRecord.getValues()[i], newRecord.getValues()[i]);
      }
    }

    if (size() >= _bufferedCapacity) {
      sortAndResize(_evictCapacity);
    }
    return true;
  }

  private void sortAndResize(int trimToSize) {
    // sort
    if (CollectionUtils.isNotEmpty(_orderBy)) {
      _records.sort(_orderByComparator);
    }

    // evict lowest (or whatever's at the bottom if sort didnt happen)
    if (_records.size() > trimToSize) {
      _records = new ArrayList<>(_records.subList(0, trimToSize));
    }

    // rebuild lookup table
    _lookupTable.clear();
    for (int i = 0; i < _records.size(); i++) {
      _lookupTable.put(_records.get(i).getKey(), i);
    }
  }


  @Override
  public boolean merge(@Nonnull Table table) {
    Iterator<Record> iterator = table.iterator();
    while (iterator.hasNext()) {
      upsert(iterator.next());
    }
    return true;
  }

  @Override
  public int size() {
    return _records.size();
  }

  @Override
  public Iterator<Record> iterator() {
    return _records.iterator();
  }

  @Override
  public void finish() {
    sortAndResize(_maxCapacity);
  }

  @Override
  public DataSchema getDataSchema() {
    return _dataSchema;
  }
}
