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
package org.apache.pinot.broker.broker;

import java.util.HashMap;
import java.util.Map;
import org.apache.helix.model.InstanceConfig;
import org.apache.pinot.broker.broker.helix.HelixBrokerStarter;
import org.apache.pinot.controller.helix.ControllerTest;
import org.apache.pinot.spi.env.PinotConfiguration;
import org.apache.pinot.spi.utils.CommonConstants;
import org.apache.pinot.spi.utils.CommonConstants.Helix;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class HelixBrokerStarterHostNameTest extends ControllerTest {

  @BeforeTest
  public void setupCluster() {
    startZk();
    startController();
  }

  @AfterTest
  public void tearDownCluster() {
    stopController();
    stopZk();
  }
  @Test
  public void testUpdateHostName() throws Exception {
    HelixBrokerStarter _brokerStarter = null;
    try {

      Map<String, Object> properties = new HashMap<>();
      properties.put(Helix.KEY_OF_BROKER_QUERY_PORT, 18099);
      properties.put(CommonConstants.Broker.BROKER_DYNAMIC_HELIX_HOST, true);
      properties.put(CommonConstants.Broker.BROKER_NETTY_HOST, "strange_name.com");
      properties.put(CommonConstants.Broker.BROKER_NETTY_PORT, 28099);
      PinotConfiguration config = new PinotConfiguration(properties);
      _brokerStarter =
        new HelixBrokerStarter(config, getHelixClusterName(), getZkUrl());
      _brokerStarter.start();
      InstanceConfig helixConfig = _helixAdmin.getInstanceConfig(getHelixClusterName(), config.getProperty(CommonConstants.Broker.CONFIG_OF_BROKER_ID));
      Assert.assertNotNull(helixConfig);
      Assert.assertEquals("strange_name.com", helixConfig.getHostName());
      Assert.assertEquals("28099", helixConfig.getPort());
    } finally {
      if (_brokerStarter != null) {
        _brokerStarter.stop();
      }
    }
  }

  @Test
  public void testSkipUpdateHostName() throws Exception {
    HelixBrokerStarter _brokerStarter = null;
    try {
      Map<String, Object> properties = new HashMap<>();
      properties.put(Helix.KEY_OF_BROKER_QUERY_PORT, 18099);
      properties.put(CommonConstants.Broker.BROKER_DYNAMIC_HELIX_HOST, false);
      properties.put(CommonConstants.Broker.BROKER_NETTY_HOST, "strange_name.com");
      properties.put(CommonConstants.Broker.BROKER_NETTY_PORT, 28099);
      PinotConfiguration config = new PinotConfiguration(properties);
      _brokerStarter =
        new HelixBrokerStarter(config, getHelixClusterName(), getZkUrl());
      _brokerStarter.start();
      InstanceConfig helixConfig = _helixAdmin.getInstanceConfig(getHelixClusterName(), config.getProperty(CommonConstants.Broker.CONFIG_OF_BROKER_ID));
      Assert.assertNotNull(helixConfig);
      Assert.assertNotEquals("strange_name.com", helixConfig.getHostName());
      Assert.assertNotEquals("28099", helixConfig.getPort());
    } finally {
      if (_brokerStarter != null) {
        _brokerStarter.stop();
      }
    }
  }

  @Test
  public void testEmptyHostName() throws Exception {
    HelixBrokerStarter _brokerStarter = null;
    try {
      Map<String, Object> properties = new HashMap<>();
      properties.put(Helix.KEY_OF_BROKER_QUERY_PORT, 18099);
      properties.put(CommonConstants.Broker.BROKER_DYNAMIC_HELIX_HOST, true);
      properties.put(CommonConstants.Broker.BROKER_NETTY_HOST, "");
      properties.put(CommonConstants.Broker.BROKER_NETTY_PORT, 28099);
      PinotConfiguration config = new PinotConfiguration(properties);
      _brokerStarter =
        new HelixBrokerStarter(config, getHelixClusterName(), getZkUrl());
      _brokerStarter.start();
      InstanceConfig helixConfig = _helixAdmin.getInstanceConfig(getHelixClusterName(), config.getProperty(CommonConstants.Broker.CONFIG_OF_BROKER_ID));
      Assert.assertNotNull(helixConfig);
      Assert.assertNotEquals("strange_name.com", helixConfig.getHostName());
    } finally {
      if (_brokerStarter != null) {
        _brokerStarter.stop();
      }
    }
  }

}
