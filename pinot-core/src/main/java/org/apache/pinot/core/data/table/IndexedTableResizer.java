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

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.apache.pinot.common.request.AggregationInfo;
import org.apache.pinot.common.request.SelectionSort;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.query.aggregation.function.AggregationFunction;
import org.apache.pinot.core.query.aggregation.function.AggregationFunctionUtils;


/**
 * Helper class for trimming and sorting records in the IndexedTable, based on the order by information
 */
class IndexedTableResizer {

  private OrderByValueExtractor[] _orderByValueExtractors;
  private Comparator<IntermediateRecord> _intermediateRecordComparator;
  private int _numOrderBy;

  IndexedTableResizer(DataSchema dataSchema, List<AggregationInfo> aggregationInfos, List<SelectionSort> orderBy) {

    int numAggregations = aggregationInfos.size();
    int numKeyColumns = dataSchema.size() - numAggregations;

    Map<String, Integer> keyIndexMap = new HashMap<>();
    Map<String, DataSchema.ColumnDataType> keyColumnDataTypeMap = new HashMap<>();
    for (int i = 0; i < numKeyColumns; i++) {
      String columnName = dataSchema.getColumnName(i);
      keyIndexMap.put(columnName, i);
      keyColumnDataTypeMap.put(columnName, dataSchema.getColumnDataType(i));
    }

    Map<String, Integer> aggregationColumnToIndex = new HashMap<>();
    Map<String, AggregationInfo> aggregationColumnToInfo = new HashMap<>();
    for (int i = 0; i < numAggregations; i++) {
      AggregationInfo aggregationInfo = aggregationInfos.get(i);
      String aggregationColumn = AggregationFunctionUtils.getAggregationColumnName(aggregationInfo);
      aggregationColumnToIndex.put(aggregationColumn, i);
      aggregationColumnToInfo.put(aggregationColumn, aggregationInfo);
    }

    _numOrderBy = orderBy.size();
    _orderByValueExtractors = new OrderByValueExtractor[_numOrderBy];
    Comparator[] comparators = new Comparator[_numOrderBy];

    for (int i = 0; i < _numOrderBy; i++) {
      SelectionSort selectionSort = orderBy.get(i);
      String column = selectionSort.getColumn();

      if (keyIndexMap.containsKey(column)) {
        int index = keyIndexMap.get(column);
        DataSchema.ColumnDataType columnDataType = keyColumnDataTypeMap.get(column);
        _orderByValueExtractors[i] = new KeyColumnExtractor(index, columnDataType);
      } else if (aggregationColumnToIndex.containsKey(column)) {
        int index = aggregationColumnToIndex.get(column);
        AggregationInfo aggregationInfo = aggregationColumnToInfo.get(column);
        AggregationFunction aggregationFunction =
            AggregationFunctionUtils.getAggregationFunctionContext(aggregationInfo).getAggregationFunction();

        if (aggregationFunction.isIntermediateResultComparable()) {
          _orderByValueExtractors[i] = new ComparableAggregationColumnExtractor(index, aggregationFunction);
        } else {
          _orderByValueExtractors[i] = new NonComparableAggregationColumnExtractor(index, aggregationFunction);
        }
      } else {
        throw new IllegalStateException("Could not find column " + column + " in data schema");
      }

      comparators[i] = Comparator.naturalOrder();
      if (!selectionSort.isIsAsc()) {
        comparators[i] = comparators[i].reversed();
      }
    }

    _intermediateRecordComparator = (o1, o2) -> {

      for (int i = 0; i < _numOrderBy; i++) {
        int result = comparators[i].compare(o1._values[i], o2._values[i]);
        if (result != 0) {
          return result;
        }
      }
      return 0;
    };
  }

  /**
   * Constructs an IntermediateRecord from Record
   * The IntermediateRecord::key is the same Record::key
   * The IntermediateRecord::values contains only the order by columns, in the query's sort sequence
   * For aggregation values in the order by, the final result is extracted if the intermediate result is non-comparable
   */
  @VisibleForTesting
  IntermediateRecord getIntermediateRecord(Record record) {
    Comparable[] intermediateRecordValues = new Comparable[_numOrderBy];
    for (int i = 0; i < _numOrderBy; i++) {
      intermediateRecordValues[i] = _orderByValueExtractors[i].extract(record);
    }
    return new IntermediateRecord(record.getKey(), intermediateRecordValues);
  }

  /**
   * Trim recordsMap to trimToSize, based on order by information
   * Resize only if number of records is greater than trimToSize
   * The resizer smartly chooses to create PQ of records to evict or records to retain, based on the number of records and the number of records to evict
   */
  void resizeRecordsMap(Map<Key, Record> recordsMap, int trimToSize) {

    int numRecordsToEvict = recordsMap.size() - trimToSize;

    if (numRecordsToEvict > 0) {
      int size;
      Comparator<IntermediateRecord> comparator;
      // TODO: compare the performance of converting to IntermediateRecord vs keeping Record, in cases where we do not need to extract final results
      PriorityQueue<IntermediateRecord> priorityQueue;

      if (numRecordsToEvict < trimToSize) { // num records to evict is smaller than num records to retain
        size = numRecordsToEvict;
        // make PQ of records to evict
        comparator = _intermediateRecordComparator;
        priorityQueue = convertToIntermediateRecordsPQ(recordsMap, size, comparator);
        for (IntermediateRecord evictRecord : priorityQueue) {
          recordsMap.remove(evictRecord._key);
        }
      } else { // num records to retain is smaller than num records to evict
        size = trimToSize;
        // make PQ of records to retain
        comparator = _intermediateRecordComparator.reversed();
        priorityQueue = convertToIntermediateRecordsPQ(recordsMap, size, comparator);
        ObjectOpenHashSet<Key> keysToRetain = new ObjectOpenHashSet<>(priorityQueue.size());
        for (IntermediateRecord retainRecord : priorityQueue) {
          keysToRetain.add(retainRecord._key);
        }
        recordsMap.keySet().retainAll(keysToRetain);
      }
    }
  }

  private PriorityQueue<IntermediateRecord> convertToIntermediateRecordsPQ(Map<Key, Record> recordsMap, int size,
      Comparator<IntermediateRecord> comparator) {
    PriorityQueue<IntermediateRecord> priorityQueue = new PriorityQueue<>(size, comparator);

    for (Record record : recordsMap.values()) {

      IntermediateRecord intermediateRecord = getIntermediateRecord(record);
      if (priorityQueue.size() < size) {
        priorityQueue.offer(intermediateRecord);
      } else {
        IntermediateRecord peek = priorityQueue.peek();
        if (priorityQueue.comparator().compare(peek, intermediateRecord) < 0) {
          priorityQueue.poll();
          priorityQueue.offer(intermediateRecord);
        }
      }
    }
    return priorityQueue;
  }

  /**
   * Resizes the recordsMap and returns a sorted list of records.
   * This method is to be called from IndexedTable::finish, if both resize and sort is needed
   */
  List<Record> resizeAndSortRecordsMap(Map<Key, Record> recordsMap, int trimToSize) {

    int numRecordsToRetain = Math.min(recordsMap.size(), trimToSize);
    if (numRecordsToRetain == 0) {
      return Collections.emptyList();
    }

    // create PQ of records to retain, so that it can further be used to return sorted list of retained records
    Comparator<IntermediateRecord> comparator = _intermediateRecordComparator.reversed();
    PriorityQueue<IntermediateRecord> priorityQueue =
        convertToIntermediateRecordsPQ(recordsMap, numRecordsToRetain, comparator);

    // created sorted list of retained records
    List<Record> sortedList = new ArrayList<>(numRecordsToRetain);
    ObjectOpenHashSet<Key> keysToRetain = new ObjectOpenHashSet<>(numRecordsToRetain);
    while (!priorityQueue.isEmpty()) {
      IntermediateRecord intermediateRecord = priorityQueue.poll();
      keysToRetain.add(intermediateRecord._key);
      Record record = recordsMap.get(intermediateRecord._key);
      sortedList.add(record);
    }
    recordsMap.keySet().retainAll(keysToRetain);
    Collections.reverse(sortedList);
    return sortedList;
  }

  /**
   * Helper class to store a subset of Record fields
   * IntermediateRecord is derived from a Record
   * Some of the main properties of an IntermediateRecord are:
   *
   * 1. Key in IntermediateRecord is expected to be identical to the one in the Record
   * 2. For values, IntermediateRecord should only have the columns needed for order by
   * 3. Inside the values, the columns should be ordered by the order by sequence
   * 4. For order by on aggregations, final results should extracted if the intermediate result is non-comparable
   */
  @VisibleForTesting
  static class IntermediateRecord {
    final Key _key;
    final Comparable[] _values;

    IntermediateRecord(Key key, Comparable[] values) {
      _key = key;
      _values = values;
    }
  }

  private static abstract class OrderByValueExtractor {
    abstract Comparable extract(Record record);
  }

  /**
   * Extractor for key column
   */
  private static class KeyColumnExtractor extends OrderByValueExtractor {
    final int _index;
    final DataSchema.ColumnDataType _columnDataType;

    KeyColumnExtractor(int index, DataSchema.ColumnDataType columnDataType) {
      _index = index;
      _columnDataType = columnDataType;
    }

    @Override
    Comparable extract(Record record) {
      Object column = record.getKey().getColumns()[_index];
      return (Comparable) column; // FIXME: is this the right way to get Comparable for key column? will it work for BYTES?
    }
  }

  /**
   * Extractor for aggregation column with comparable intermediate result
   */
  private static class ComparableAggregationColumnExtractor extends OrderByValueExtractor {
    final int _index;
    final AggregationFunction _aggregationFunction;

    ComparableAggregationColumnExtractor(int index, AggregationFunction aggregationFunction) {
      _index = index;
      _aggregationFunction = aggregationFunction;
    }

    @Override
    Comparable extract(Record record) {
      Object aggregationColumn = record.getValues()[_index];
      return (Comparable) aggregationColumn;
    }
  }

  /**
   * Extractor for aggregation column with non-comparable intermediate result
   */
  private static class NonComparableAggregationColumnExtractor extends OrderByValueExtractor {
    final int _index;
    final AggregationFunction _aggregationFunction;

    NonComparableAggregationColumnExtractor(int index, AggregationFunction aggregationFunction) {
      _index = index;
      _aggregationFunction = aggregationFunction;
    }

    @Override
    Comparable extract(Record record) {
      Object aggregationColumn = record.getValues()[_index];
      return _aggregationFunction.extractFinalResult(aggregationColumn);
    }
  }
}
