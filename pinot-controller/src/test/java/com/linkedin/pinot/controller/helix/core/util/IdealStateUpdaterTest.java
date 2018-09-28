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
package com.linkedin.pinot.controller.helix.core.util;

import com.linkedin.pinot.common.utils.EqualityUtils;
import com.linkedin.pinot.controller.helix.core.IdealStateUpdater;
import com.linkedin.pinot.controller.helix.core.rebalance.RebalanceUserConfigConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.helix.model.IdealState;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class IdealStateUpdaterTest {

  private IdealState current;
  private final String segmentId = "segment1";
  private Configuration downtime = new PropertiesConfiguration();
  private Configuration noDowntime = new PropertiesConfiguration();

  @BeforeMethod
  public void setup() {
    current = new IdealState("rebalance");
    current.setPartitionState(segmentId, "host1", "ONLINE");
    current.setPartitionState(segmentId, "host2", "ONLINE");

    downtime.setProperty(RebalanceUserConfigConstants.DOWNTIME, true);
    noDowntime.setProperty(RebalanceUserConfigConstants.DOWNTIME, false);
  }

  // no-downtime update with common elements - target state is set in one go
  @Test
  public void noDowntimeUpdateWithCommonElements() {
    Map<String, String> targetMap = new HashMap<>();
    targetMap.put("host1", "ONLINE");
    targetMap.put("host3", "ONLINE");

    Map<String, String> srcMap = current.getRecord().getMapField(segmentId);
    Assert.assertEquals(srcMap.size(), 2);
    IdealStateUpdater updater = new IdealStateUpdater(null, null, null);
    updater.updateSegmentIfNeeded(segmentId, srcMap, targetMap, current, noDowntime);

    Map<String, String> tempMap = current.getRecord().getMapField(segmentId);
    Assert.assertTrue(EqualityUtils.isEqual(tempMap, targetMap));
  }

  // downtime update with common elements - target state is set in one go
  @Test
  public void downtimeUpdateWithCommonElements() {
    Map<String, String> targetMap = new HashMap<>();
    targetMap.put("host1", "ONLINE");
    targetMap.put("host3", "ONLINE");

    Map<String, String> srcMap = current.getRecord().getMapField(segmentId);
    Assert.assertEquals(srcMap.size(), 2);
    IdealStateUpdater updater = new IdealStateUpdater(null, null, null);
    updater.updateSegmentIfNeeded(segmentId, srcMap, targetMap, current, downtime);

    Map<String, String> tempMap = current.getRecord().getMapField(segmentId);
    Assert.assertTrue(EqualityUtils.isEqual(tempMap, targetMap));
  }

  // no-downtime update without common elements - target state is updated to have one up replica
  @Test
  public void NoDowntimeUpdateWithNoCommonElements() {
    Map<String, String> targetMap = new HashMap<>();
    targetMap.put("host4", "ONLINE");
    targetMap.put("host3", "ONLINE");

    Map<String, String> srcMap = current.getRecord().getMapField(segmentId);
    IdealStateUpdater updater = new IdealStateUpdater(null, null, null);
    updater.updateSegmentIfNeeded(segmentId, srcMap, targetMap, current, noDowntime);

    Map<String, String> tempMap = current.getRecord().getMapField(segmentId);
    Set<String> targetHosts = new HashSet<String>(Arrays.asList("host3", "host4"));
    Set<String> srcHosts = new HashSet<String>(Arrays.asList("host1", "host2"));
    Assert.assertEquals(tempMap.size(), targetHosts.size());
    for (String instance : tempMap.keySet()) {
      Assert.assertTrue(targetHosts.contains(instance) || srcHosts.contains(instance));
    }
  }

  // downtime update without common elements - target state is set in one go
  @Test
  public void downtimeUpdateWithNoCommonElements() {
    Map<String, String> targetMap = new HashMap<>();
    targetMap.put("host4", "ONLINE");
    targetMap.put("host3", "ONLINE");

    Map<String, String> srcMap = current.getRecord().getMapField(segmentId);
    IdealStateUpdater updater = new IdealStateUpdater(null, null, null);
    updater.updateSegmentIfNeeded(segmentId, srcMap, targetMap, current, downtime);

    Map<String, String> tempMap = current.getRecord().getMapField(segmentId);
    Assert.assertTrue(EqualityUtils.isEqual(tempMap, targetMap));
  }
}
