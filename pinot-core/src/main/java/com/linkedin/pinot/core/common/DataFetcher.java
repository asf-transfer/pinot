/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.common;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.data.FieldSpec;
import com.linkedin.pinot.core.plan.DocIdSetPlanNode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.linkedin.pinot.core.operator.BaseOperator;
import com.linkedin.pinot.core.segment.index.readers.Dictionary;


/**
 * DataFetcher is a higher level abstraction for data fetching. Given an index segment, DataFetcher can manage the
 * DataSource, Dictionary, BlockValSet and BlockValIterator for this segment, preventing redundant construction for
 * these instances. DataFetcher can be used by both selection, aggregation and group-by data fetching process, reducing
 * duplicate codes and garbage collection.
 */
public class DataFetcher {
  private static final BlockId BLOCK_ZERO = new BlockId(0);

  private final Map<String, Dictionary> _columnToDictionaryMap;
  private final Map<String, BlockValSet> _columnToBlockValSetMap;
  private final Map<String, BlockValIterator> _columnToBlockValIteratorMap;
  private final Map<String, BlockMetadata> _columnToBlockMetadataMap;

  // Map from MV column name to max number of entries for the column.
  private final Map<String, Integer> _columnToMaxNumMultiValuesMap;

  // Re-usable array for all dictionary ids in the block, of a single valued column
  private final int[] _reusableDictIds;

  // Re-usable array to store MV dictionary id's for a given docId
  private final int[] _reusableMVDictIds;

  /**
   * Constructor for DataFetcher.
   *
   * @param columnToDataSourceMap Map from column name to data source
   */
  public DataFetcher(Map<String, BaseOperator> columnToDataSourceMap) {
    _columnToDictionaryMap = new HashMap<>();
    _columnToBlockValSetMap = new HashMap<>();
    _columnToBlockValIteratorMap = new HashMap<>();
    _columnToBlockMetadataMap = new HashMap<>();

    _reusableDictIds = new int[DocIdSetPlanNode.MAX_DOC_PER_CALL];
    _columnToMaxNumMultiValuesMap = new HashMap<>();

    int reusableMVDictIdSize = 0;
    for (String column : columnToDataSourceMap.keySet()) {
      BaseOperator dataSource = columnToDataSourceMap.get(column);
      Block dataSourceBlock = dataSource.nextBlock(BLOCK_ZERO);
      BlockMetadata metadata = dataSourceBlock.getMetadata();
      _columnToDictionaryMap.put(column, metadata.getDictionary());

      BlockValSet blockValSet = dataSourceBlock.getBlockValueSet();
      _columnToBlockValSetMap.put(column, blockValSet);
      _columnToBlockValIteratorMap.put(column, blockValSet.iterator());
      _columnToBlockMetadataMap.put(column, metadata);

      int maxNumberOfMultiValues = metadata.getMaxNumberOfMultiValues();
      _columnToMaxNumMultiValuesMap.put(column, maxNumberOfMultiValues);
      reusableMVDictIdSize = Math.max(reusableMVDictIdSize, maxNumberOfMultiValues);
    }

    _reusableMVDictIds = new int[reusableMVDictIdSize];
  }

  /**
   * Given a column, fetch its dictionary.
   *
   * @param column column name.
   * @return dictionary associated with this column.
   */
  public Dictionary getDictionaryForColumn(String column) {
    return _columnToDictionaryMap.get(column);
  }

  /**
   * Given a column, fetch its block value set.
   *
   * @param column column name.
   * @return block value set associated with this column.
   */
  public BlockValSet getBlockValSetForColumn(String column) {
    return _columnToBlockValSetMap.get(column);
  }

  /**
   * Returns the BlockValIterator for the specified column.
   *
   * @param column Column for which to return the blockValIterator.
   * @return BlockValIterator for the column.
   */
  public BlockValIterator getBlockValIteratorForColumn(String column) {
    return _columnToBlockValIteratorMap.get(column);
  }

  public BlockMetadata getBlockMetadataFor(String column) {
    return _columnToBlockMetadataMap.get(column);
  }

  /**
   * Fetch the dictionary Ids for a single value column.
   *
   * @param column column name.
   * @param inDocIds document Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outDictIds dictionary Id array buffer.
   * @param outStartPos output start position.
   */
  public void fetchSingleDictIds(String column, int[] inDocIds, int inStartPos, int length, int[] outDictIds, int outStartPos) {
    BlockValSet blockValSet = getBlockValSetForColumn(column);
    blockValSet.getDictionaryIds(inDocIds, inStartPos, length, outDictIds, outStartPos);
  }

  /**
   * Fetch the dictionary Ids for a multi value column.
   *
   * @param column column name.
   * @param inDocIds document Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outDictIdsArray dictionary Id array array buffer.
   * @param outStartPos output start position.
   * @param tempDictIdArray temporary holding dictIds read from BlockMultiValIterator.
   *          Array size has to be >= max number of entries for this column.
   */
  public void fetchMultiValueDictIds(String column, int[] inDocIds, int inStartPos, int length, int[][] outDictIdsArray, int outStartPos,
      int[] tempDictIdArray) {
    BlockMultiValIterator iterator = (BlockMultiValIterator) getBlockValSetForColumn(column).iterator();
    for (int i = inStartPos; i < inStartPos + length; i++, outStartPos++) {
      iterator.skipTo(inDocIds[i]);
      int dictIdLength = iterator.nextIntVal(tempDictIdArray);
      outDictIdsArray[outStartPos] = Arrays.copyOfRange(tempDictIdArray, 0, dictIdLength);
    }
  }

  /**
   * Fetches dictionary ids for a given docId of a multi-valued column.
   * Expects outDictIds to be sufficiently large to accommodate all values.
   *
   * @param column Column name
   * @param inDocId Input docId
   * @param outDictIds output array
   * @return Dictionary ids for all multi-values for the given docId
   */
  public int fetchDictIdsForDocId(String column, int inDocId, int[] outDictIds) {
    BlockMultiValIterator iterator = (BlockMultiValIterator) getBlockValSetForColumn(column).iterator();
    iterator.skipTo(inDocId);
    return iterator.nextIntVal(outDictIds);
  }


  /**
   * For a given multi-value column, trying to get the max number of
   * entries per row.
   *
   * @param column Column for which to get the max number of multi-values.
   * @return max number of entries for a given column.
   */
  public int getMaxNumberOfEntriesForColumn(String column) {
    return _columnToMaxNumMultiValuesMap.get(column);
  }

  /**
   * Fetch the values for a single int value column.
   *
   * @param column column name.
   * @param inDocIds doc Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchIntValues(String column, int[] inDocIds, int inStartPos, int length, int[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);
    dictionary.readIntValues(_reusableDictIds, 0, length, outValues, outStartPos);
  }

  /**
   * Fetch the values for a single long value column.
   *
   * @param column column name.
   * @param inDocIds doc Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchLongValues(String column, int[] inDocIds, int inStartPos, int length, long[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);
    dictionary.readLongValues(_reusableDictIds, 0, length, outValues, outStartPos);
  }

  /**
   * Fetch the values for a single float value column.
   *
   * @param column column name.
   * @param inDocIds doc Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchFloatValues(String column, int[] inDocIds, int inStartPos, int length, float[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);
    dictionary.readFloatValues(_reusableDictIds, 0, length, outValues, outStartPos);
  }

  /**
   * Fetch the values for a single double value column.
   *
   * @param column column name.
   * @param inDocIds dictionary Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchDoubleValues(String column, int[] inDocIds, int inStartPos, int length, double[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);
    dictionary.readDoubleValues(_reusableDictIds, 0, length, outValues, outStartPos);
  }

  /**
   * Fetch the double values for a multi-valued column.
   *
   * @param column column name.
   * @param inDocIds dictionary Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchDoubleValues(String column, int[] inDocIds, int inStartPos, int length, double[][] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);

    int inEndPos = inStartPos + length;
    for (int i = inStartPos; i < inEndPos; i++, outStartPos++) {
      int numValues = fetchDictIdsForDocId(column, inDocIds[i], _reusableMVDictIds);
      outValues[outStartPos] = new double[numValues];
      dictionary.readDoubleValues(_reusableMVDictIds, 0, numValues, outValues[outStartPos], 0);
    }
  }

  /**
   *
   * @param column Column for which to fetch the values
   * @param inDocIds Array of docIds for which to fetch the values
   * @param outValues Array of strings where output will be written
   * @param length Length of input docIds
   */
  public void fetchStringValues(String column, int[] inDocIds, int inStartPos, int length, String[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);
    dictionary.readStringValues(_reusableDictIds, 0, length, outValues, outStartPos);
  }

  /**
   *
   * @param column Column for which to fetch the values
   * @param inDocIds Array of docIds for which to fetch the values
   * @param outValues Array of strings where output will be written
   * @param length Length of input docIds
   */
  public void fetchStringValues(String column, int[] inDocIds, int inStartPos, int length, String[][] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);

    int inEndPos = inStartPos + length;
    for (int i = inStartPos; i < inEndPos; i++, outStartPos++) {
      int numValues = fetchDictIdsForDocId(column, inDocIds[i], _reusableMVDictIds);
      outValues[outStartPos] = new String[numValues];
      dictionary.readStringValues(_reusableMVDictIds, 0, numValues, outValues[outStartPos], 0);
    }
  }

  /**
   * Fetch the hash code values for a single value column.
   *
   * @param column column name.
   * @param inDocIds doc Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchHashCodes(String column, int[] inDocIds, int inStartPos, int length, double[] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);
    fetchSingleDictIds(column, inDocIds, inStartPos, length, _reusableDictIds, 0);

    for (int i = 0; i < length; i++, outStartPos++) {
      outValues[outStartPos] = dictionary.get(_reusableDictIds[i]).hashCode();
    }
  }

  /**
   * Fetch the hash code values for a single value column.
   *
   * @param column column name.
   * @param inDocIds doc Id array.
   * @param inStartPos input start position.
   * @param length input length.
   * @param outValues value array buffer.
   * @param outStartPos output start position.
   */
  public void fetchHashCodes(String column, int[] inDocIds, int inStartPos, int length, double[][] outValues, int outStartPos) {
    Dictionary dictionary = getDictionaryForColumn(column);

    int inEndPos = inStartPos + length;
    for (int i = inStartPos; i < inEndPos; i++, outStartPos++) {
      int numValues = fetchDictIdsForDocId(column, inDocIds[i], _reusableMVDictIds);
      outValues[outStartPos] = new double[numValues];
      for (int j = 0; j < numValues; j++) {
        outValues[outStartPos][j] = dictionary.get(_reusableMVDictIds[j]).hashCode();
      }
    }
  }

  /**
   * Returns the data type for the specified column.
   *
   * @param column Name of column for which to return the data type.
   * @return Data type of the column.
   */
  public FieldSpec.DataType getDataType(String column) {
    BlockMetadata blockMetadata = _columnToBlockMetadataMap.get(column);
    Preconditions.checkNotNull(blockMetadata, "Invalid column " + column + " specified in DataFetcher.");
    return blockMetadata.getDataType();
  }
}
