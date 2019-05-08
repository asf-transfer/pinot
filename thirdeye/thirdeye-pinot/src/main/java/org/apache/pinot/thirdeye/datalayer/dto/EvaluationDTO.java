/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one
 *  * or more contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  The ASF licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 *
 */

package org.apache.pinot.thirdeye.datalayer.dto;

import com.google.common.base.Preconditions;
import org.apache.pinot.thirdeye.dataframe.DataFrame;
import org.apache.pinot.thirdeye.dataframe.Series;
import org.apache.pinot.thirdeye.datalayer.pojo.EvaluationBean;
import org.apache.pinot.thirdeye.detection.PredictionResult;

import static org.apache.pinot.thirdeye.dataframe.DoubleSeries.*;
import static org.apache.pinot.thirdeye.dataframe.util.DataFrameUtils.*;


public class EvaluationDTO extends EvaluationBean {
  public static EvaluationDTO fromPredictionResult(PredictionResult predictionResult, long startTime, long endTime,
      long detectionConfigId) {
    EvaluationDTO evaluation = new EvaluationDTO();
    evaluation.setDetectionConfigId(detectionConfigId);
    evaluation.setStartTime(startTime);
    evaluation.setEndTime(endTime);
    evaluation.setDetectorName(predictionResult.getDetectorName());
    evaluation.setMetricUrn(predictionResult.getMetricUrn());
    evaluation.setMape(calculateMape(predictionResult.getPredictedTimeSeries()));

    return evaluation;
  }

  private static double calculateMape(DataFrame predictedTimeSeires) {
    Preconditions.checkArgument(predictedTimeSeires.contains(COL_TIME));
    if (!predictedTimeSeires.contains(COL_CURRENT) || !predictedTimeSeires.contains(COL_VALUE)) {
      return Double.NaN;
    }

    predictedTimeSeires.getDoubles(COL_VALUE).map((Series.DoubleFunction) values -> {

    })
        divide(predictedTimeSeires.getDoubles(COL_CURRENT)).abs();
    return 0;
  }
}
