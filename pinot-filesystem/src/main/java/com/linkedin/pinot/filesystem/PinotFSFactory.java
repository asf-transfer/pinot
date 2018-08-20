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

package com.linkedin.pinot.filesystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This factory class initializes the PinotFS class. It creates a PinotFS object based on the URI found.
 */
public class PinotFSFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(PinotFSFactory.class);
  private static final String DEFAULT_FS_SCHEME = "file";

  private static Map<String, PinotFS> _fileSystemMap = new HashMap<>();

  // Prevent factory from being instantiated.
  private PinotFSFactory() {

  }

  public static void init(Configuration fsConfig) {
    Iterator<String> keys = fsConfig.subset("class").getKeys();
    while (keys.hasNext()) {
      String key = keys.next();
      String fsClassName = (String) fsConfig.getProperty(key);

      try {
        PinotFS pinotFS = (PinotFS) Class.forName(fsClassName).newInstance();
        pinotFS.init(fsConfig.subset("config"));

        _fileSystemMap.put(key, pinotFS);
      } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
        LOGGER.error("Could not instantiate file system for class {}", fsClassName, e);
        throw new RuntimeException(e);
      }
    }

    if (!_fileSystemMap.containsKey(DEFAULT_FS_SCHEME)) {
      LOGGER.info("Pinot file system not configured, configuring LocalPinotFS as default");
      _fileSystemMap.put(DEFAULT_FS_SCHEME, new LocalPinotFS());
    }
  }

  public static PinotFS create(String scheme) {
    PinotFS pinotFS = _fileSystemMap.get(scheme);
    if (pinotFS == null) {
      throw new RuntimeException("Pinot file system not configured for scheme: " + scheme);
    }
    return pinotFS;
  }
}
