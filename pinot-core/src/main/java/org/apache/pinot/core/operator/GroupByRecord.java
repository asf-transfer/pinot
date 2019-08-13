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
package org.apache.pinot.core.operator;

import com.google.common.base.Joiner;
import javax.annotation.Nonnull;
import org.apache.pinot.common.utils.EqualityUtils;
import org.apache.pinot.core.query.aggregation.function.AggregationFunction;


public class GroupByRecord {
  private String _stringKey;
  private String[] _arrayKey;
  private Object[] _aggregationResults;

  private static String SEPARATOR = "\t";

  // TODO: can this be just 1 array
  public GroupByRecord(@Nonnull String stringKey, @Nonnull Object[] aggregationResults) {
    _stringKey = stringKey;
    _arrayKey = stringKey.split(SEPARATOR);
    _aggregationResults = aggregationResults;
  }

  @Nonnull
  public String[] getArrayKey() {
    return _arrayKey;
  }

  @Nonnull
  public Object[] getAggregationResults() {
    return _aggregationResults;
  }

  @Nonnull
  public String getStringKey() {
    return _stringKey;
  }

  public void merge(Object[] resultToMerge, AggregationFunction[] aggregationFunctions, int numAggregationFunctions) {
    for (int i = 0; i < numAggregationFunctions; i++) {
      _aggregationResults[i] = aggregationFunctions[i].merge(_aggregationResults[i], resultToMerge[i]);
    }
  }

  public static String getGroupByKey(String[] groupByArray) {
    return Joiner.on(SEPARATOR).join(groupByArray);
  }

  @Override
  public boolean equals(Object o) {
    if (EqualityUtils.isSameReference(this, o)) {
      return true;
    }

    if (EqualityUtils.isNullOrNotSameClass(this, o)) {
      return false;
    }

    GroupByRecord that = (GroupByRecord) o;

    return EqualityUtils.isEqual(_stringKey, that._stringKey) && EqualityUtils.isEqual(_aggregationResults,
        that._aggregationResults);
  }

  @Override
  public int hashCode() {
    int result = EqualityUtils.hashCodeOf(_stringKey);
    result = EqualityUtils.hashCodeOf(result, _aggregationResults);
    return result;
  }
}
