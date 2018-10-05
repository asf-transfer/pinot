/**
 * Copyright (C) 2014-2018 LinkedIn Corp. (pinot-core@linkedin.com)
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
package com.linkedin.pinot.core.startree.v2;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.core.data.aggregator.PercentileEstValueAggregator;
import com.linkedin.pinot.core.data.aggregator.ValueAggregator;
import com.linkedin.pinot.core.query.aggregation.function.PercentileEstAggregationFunction;
import com.linkedin.pinot.core.query.aggregation.function.customobject.QuantileDigest;
import java.util.Random;

import static org.testng.Assert.*;


public class PercentileEstStarTreeV2Test extends BaseStarTreeV2Test<Object, QuantileDigest> {

  @Override
  ValueAggregator<Object, QuantileDigest> getValueAggregator() {
    return new PercentileEstValueAggregator();
  }

  @Override
  DataType getRawValueType() {
    return DataType.LONG;
  }

  @Override
  Object getRandomRawValue(Random random) {
    return random.nextLong();
  }

  @Override
  void assertAggregatedValue(QuantileDigest starTreeResult, QuantileDigest nonStarTreeResult) {
    double delta = Long.MAX_VALUE * PercentileEstAggregationFunction.DEFAULT_MAX_ERROR * 2;
    for (int i = 0; i <= 100; i++) {
      assertEquals(starTreeResult.getQuantile(i / 100), nonStarTreeResult.getQuantile(i / 100), delta);
    }
  }
}
