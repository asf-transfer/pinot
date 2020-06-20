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

package org.apache.pinot.thirdeye.common.restclient;

import java.util.Map;
import org.apache.pinot.thirdeye.auth.ThirdEyePrincipal;
import org.apache.pinot.thirdeye.datalayer.bao.DAOTestBase;
import org.apache.pinot.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import org.apache.pinot.thirdeye.datasource.DAORegistry;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class TestThirdEyeRcaRestClient {

  private static String SAMPLE_RESPONSE = "{ \"relatedMetrics\": \"{}\"," + " \"relatedEvents\": \"{}\"," + " \"cubeResults\": \"{}\"}";

  private DAOTestBase testDAOProvider;
  private long anomalyId;

  @BeforeMethod
  public void beforeMethod() throws Exception {
    this.testDAOProvider = DAOTestBase.getInstance();
    DAORegistry daoRegistry = DAORegistry.getInstance();

    MergedAnomalyResultDTO anomaly = new MergedAnomalyResultDTO();
    anomaly.setCollection("collection");
    anomaly.setMetric("metric");
    anomalyId = daoRegistry.getMergedAnomalyResultDAO().save(anomaly);
  }
  @Test
  public void testGetAllHighlights() throws Exception {
    AbstractRestClient.HttpURLConnectionFactory factory = MockAbstractRestClient.setupMock(
        "http://localhost:1426/rootcause/highlights?anomalyId=1",
        SAMPLE_RESPONSE, null);

    ThirdEyePrincipal principal = new ThirdEyePrincipal();
    principal.setSessionKey("dummy");
    ThirdEyeRcaRestClient client = new ThirdEyeRcaRestClient(factory, principal);
    Map<String, Object> result = client.getAllHighlights(anomalyId);

    Assert.assertTrue(result.containsKey("relatedMetrics"));
    Assert.assertTrue(result.containsKey("relatedEvents"));
    Assert.assertTrue(result.containsKey("cubeResults"));
  }
}
