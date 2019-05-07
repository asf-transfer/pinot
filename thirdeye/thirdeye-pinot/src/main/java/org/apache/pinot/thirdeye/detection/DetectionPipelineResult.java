/*
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

package org.apache.pinot.thirdeye.detection;

import java.util.Collections;
import org.apache.pinot.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DetectionPipelineResult {
  public static String DIAGNOSTICS_DATA = "data";
  public static String DIAGNOSTICS_CHANGE_POINTS = "changepoints";

  private Map<String, Object> diagnostics;
  private final List<MergedAnomalyResultDTO> anomalies;
  private final long lastTimestamp;
  private final List<PredictionResult> predictions;

  public DetectionPipelineResult(List<MergedAnomalyResultDTO> anomalies) {
    this.anomalies = anomalies;
    this.lastTimestamp = getMaxTime(anomalies);
    this.diagnostics = new HashMap<>();
    this.predictions = Collections.emptyList();
  }

  public DetectionPipelineResult(List<MergedAnomalyResultDTO> anomalies, long lastTimestamp) {
    this.anomalies = anomalies;
    this.lastTimestamp = lastTimestamp;
    this.predictions = Collections.emptyList();
    this.diagnostics = new HashMap<>();
  }

  public DetectionPipelineResult(List<MergedAnomalyResultDTO> anomalies, long lastTimestamp,
      List<PredictionResult> predictedTimeSeries) {
    this.anomalies = anomalies;
    this.lastTimestamp = lastTimestamp;
    this.predictions = predictedTimeSeries;
    this.diagnostics = new HashMap<>();
  }

  public List<PredictionResult> getPredictions() {
    return predictions;
  }

  public List<MergedAnomalyResultDTO> getAnomalies() {
    return anomalies;
  }


  public long getLastTimestamp() {
    return lastTimestamp;
  }


  public Map<String, Object> getDiagnostics() {
    return diagnostics;
  }

  public DetectionPipelineResult setDiagnostics(Map<String, Object> diagnostics) {
    this.diagnostics = diagnostics;
    return this;
  }

  private static long getMaxTime(Iterable<MergedAnomalyResultDTO> anomalies) {
    long maxTime = -1;
    for (MergedAnomalyResultDTO anomaly : anomalies) {
      maxTime = Math.max(maxTime, anomaly.getEndTime());
    }
    return maxTime;
  }
}
