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
package com.linkedin.pinot.core.data.manager.offline;

import com.linkedin.pinot.common.config.TableConfig;
import com.linkedin.pinot.common.data.Schema;
import com.linkedin.pinot.core.indexsegment.IndexSegment;
import com.linkedin.pinot.core.indexsegment.SegmentLoader;
import com.linkedin.pinot.core.metadata.instance.InstanceZKMetadata;
import com.linkedin.pinot.core.metadata.segment.SegmentMetadata;
import com.linkedin.pinot.core.metadata.segment.SegmentZKMetadata;
import com.linkedin.pinot.core.segment.index.loader.IndexLoadingConfig;
import java.io.File;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.helix.ZNRecord;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.slf4j.LoggerFactory;


/**
 * An implementation of offline TableDataManager.
 * Provide add and remove segment functionality.
 */
public class OfflineTableDataManager extends AbstractTableDataManager {

  public OfflineTableDataManager() {
    super();
  }

  protected void doShutdown() {
  }

  protected void doInit() {
    LOGGER = LoggerFactory.getLogger(_tableName + "-OfflineTableDataManager");
  }

  @Override
  public void addSegment(@Nonnull SegmentMetadata segmentMetadata, @Nonnull IndexLoadingConfig indexLoadingConfig,
      @Nullable Schema schema)
      throws Exception {
    IndexSegment indexSegment =
        SegmentLoader.load(new File(segmentMetadata.getIndexDir()), indexLoadingConfig, schema);
    addSegment(indexSegment);
  }

  @Override
  public void addSegment(@Nonnull ZkHelixPropertyStore<ZNRecord> propertyStore, @Nonnull TableConfig tableConfig,
      @Nullable InstanceZKMetadata instanceZKMetadata, @Nonnull SegmentZKMetadata segmentZKMetadata,
      @Nonnull IndexLoadingConfig indexLoadingConfig)
      throws Exception {
    throw new UnsupportedOperationException(
        "Unsupported adding segment: " + segmentZKMetadata.getSegmentName() + " to REALTIME table: " + segmentZKMetadata
            .getTableName() + " using OfflineTableDataManager");
  }
}
