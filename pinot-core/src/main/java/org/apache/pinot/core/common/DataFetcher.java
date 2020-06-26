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
package org.apache.pinot.core.common;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.pinot.core.plan.DocIdSetPlanNode;
import org.apache.pinot.core.segment.index.readers.Dictionary;


/**
 * DataFetcher is a higher level abstraction for data fetching. Given the DataSource, DataFetcher can manage the
 * ColumnValueReader and Dictionary for the column, preventing redundant construction for these instances. DataFetcher
 * can be used by both selection, aggregation and group-by data fetching process, reducing duplicate codes and garbage
 * collection.
 */
public class DataFetcher {
  // Thread local (reusable) buffer for single-valued column dictionary Ids
  private static final ThreadLocal<int[]> THREAD_LOCAL_DICT_IDS =
      ThreadLocal.withInitial(() -> new int[DocIdSetPlanNode.MAX_DOC_PER_CALL]);

  // TODO: Merge _valueReaderMap and _dictionaryMap as one map to reduce the map lookups
  private final Map<String, ColumnValueReader> _valueReaderMap;
  private final Map<String, Dictionary> _dictionaryMap;
  private final int[] _reusableMVDictIds;

  /**
   * Constructor for DataFetcher.
   *
   * @param dataSourceMap Map from column to data source
   */
  public DataFetcher(Map<String, DataSource> dataSourceMap) {
    int numColumns = dataSourceMap.size();
    _valueReaderMap = new HashMap<>(numColumns);
    _dictionaryMap = new HashMap<>(numColumns);

    int maxNumValuesPerMVEntry = 0;
    for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
      String column = entry.getKey();
      DataSource dataSource = entry.getValue();
      _valueReaderMap.put(column, dataSource.getValueReader());
      _dictionaryMap.put(column, dataSource.getDictionary());
      DataSourceMetadata dataSourceMetadata = dataSource.getDataSourceMetadata();
      if (!dataSourceMetadata.isSingleValue()) {
        maxNumValuesPerMVEntry = Math.max(maxNumValuesPerMVEntry, dataSourceMetadata.getMaxNumValuesPerMVEntry());
      }
    }

    _reusableMVDictIds = new int[maxNumValuesPerMVEntry];
  }

  /**
   * SINGLE-VALUED COLUMN API
   */

  /**
   * Fetch the dictionary Ids for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outDictIds Buffer for output
   */
  public void fetchDictIds(String column, int[] inDocIds, int length, int[] outDictIds) {
    _valueReaderMap.get(column).getIntValues(inDocIds, length, outDictIds);
  }

  /**
   * Fetch the int values for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchIntValues(String column, int[] inDocIds, int length, int[] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readIntValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getIntValues(inDocIds, length, outValues);
    }
  }

  /**
   * Fetch the long values for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchLongValues(String column, int[] inDocIds, int length, long[] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readLongValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getLongValues(inDocIds, length, outValues);
    }
  }

  /**
   * Fetch the float values for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchFloatValues(String column, int[] inDocIds, int length, float[] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readFloatValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getFloatValues(inDocIds, length, outValues);
    }
  }

  /**
   * Fetch the double values for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchDoubleValues(String column, int[] inDocIds, int length, double[] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readDoubleValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getDoubleValues(inDocIds, length, outValues);
    }
  }

  /**
   * Fetch the string values for a single-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchStringValues(String column, int[] inDocIds, int length, String[] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readStringValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getStringValues(inDocIds, length, outValues);
    }
  }

  /**
   * Fetch byte[] values for a single-valued column.
   *
   * @param column Column to read
   * @param inDocIds Input document id's buffer
   * @param length Number of input document id'
   * @param outValues Buffer for output
   */
  public void fetchBytesValues(String column, int[] inDocIds, int length, byte[][] outValues) {
    Dictionary dictionary = _dictionaryMap.get(column);
    if (dictionary != null) {
      int[] dictIds = THREAD_LOCAL_DICT_IDS.get();
      fetchDictIds(column, inDocIds, length, dictIds);
      dictionary.readBytesValues(dictIds, length, outValues);
    } else {
      _valueReaderMap.get(column).getBytesValues(inDocIds, length, outValues);
    }
  }

  /**
   * MULTI-VALUED COLUMN API
   */

  /**
   * Fetch the dictionary Ids for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outDictIds Buffer for output
   */
  public void fetchDictIds(String column, int[] inDocIds, int length, int[][] outDictIds) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outDictIds[i] = Arrays.copyOfRange(_reusableMVDictIds, 0, numMultiValues);
    }
  }

  /**
   * Fetch the int values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchIntValues(String column, int[] inDocIds, int length, int[][] outValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outValues[i] = new int[numMultiValues];
      _dictionaryMap.get(column).readIntValues(_reusableMVDictIds, numMultiValues, outValues[i]);
    }
  }

  /**
   * Fetch the long values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchLongValues(String column, int[] inDocIds, int length, long[][] outValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outValues[i] = new long[numMultiValues];
      _dictionaryMap.get(column).readLongValues(_reusableMVDictIds, numMultiValues, outValues[i]);
    }
  }

  /**
   * Fetch the float values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchFloatValues(String column, int[] inDocIds, int length, float[][] outValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outValues[i] = new float[numMultiValues];
      _dictionaryMap.get(column).readFloatValues(_reusableMVDictIds, numMultiValues, outValues[i]);
    }
  }

  /**
   * Fetch the double values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchDoubleValues(String column, int[] inDocIds, int length, double[][] outValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outValues[i] = new double[numMultiValues];
      _dictionaryMap.get(column).readDoubleValues(_reusableMVDictIds, numMultiValues, outValues[i]);
    }
  }

  /**
   * Fetch the string values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outValues Buffer for output
   */
  public void fetchStringValues(String column, int[] inDocIds, int length, String[][] outValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      int numMultiValues = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
      outValues[i] = new String[numMultiValues];
      _dictionaryMap.get(column).readStringValues(_reusableMVDictIds, numMultiValues, outValues[i]);
    }
  }

  /**
   * Fetch the number of values for a multi-valued column.
   *
   * @param column Column name
   * @param inDocIds Input document Ids buffer
   * @param length Number of input document Ids
   * @param outNumValues Buffer for output
   */
  public void fetchNumValues(String column, int[] inDocIds, int length, int[] outNumValues) {
    ColumnValueReader valueReader = _valueReaderMap.get(column);
    for (int i = 0; i < length; i++) {
      outNumValues[i] = valueReader.getIntValues(inDocIds[i], _reusableMVDictIds);
    }
  }
}
