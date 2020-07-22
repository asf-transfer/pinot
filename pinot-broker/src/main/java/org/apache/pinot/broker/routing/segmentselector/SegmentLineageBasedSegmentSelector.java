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
package org.apache.pinot.broker.routing.segmentselector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.helix.ZNRecord;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.apache.pinot.common.lineage.LineageEntry;
import org.apache.pinot.common.lineage.LineageEntryState;
import org.apache.pinot.common.lineage.SegmentLineage;
import org.apache.pinot.common.lineage.SegmentLineageAccessHelper;


public class SegmentLineageBasedSegmentSelector {
  private final String _tableNameWithType;
  private final ZkHelixPropertyStore<ZNRecord> _propertyStore;

  private volatile Set<String> _segmentsToRemove;

  public SegmentLineageBasedSegmentSelector(String tableNameWithType, ZkHelixPropertyStore<ZNRecord> propertyStore) {
    _tableNameWithType = tableNameWithType;
    _propertyStore = propertyStore;
  }

  public void init() {
    onExternalViewChange();
  }

  public void onExternalViewChange() {
    // Fetch segment lineage
    SegmentLineage segmentLineage = SegmentLineageAccessHelper.getSegmentLineage(_propertyStore, _tableNameWithType);
    Set<String> segmentsToRemove = new HashSet<>();
    if (segmentLineage != null) {
      for (String lineageEntryId : segmentLineage.getLineageEntryIds()) {
        LineageEntry lineageEntry = segmentLineage.getLineageEntry(lineageEntryId);
        if (lineageEntry.getState() == LineageEntryState.COMPLETED) {
          segmentsToRemove.addAll(lineageEntry.getSegmentsFrom());
        } else {
          segmentsToRemove.addAll(lineageEntry.getSegmentsTo());
        }
      }
    }
    _segmentsToRemove = Collections.unmodifiableSet(segmentsToRemove);
  }

  public Set<String> computeSegmentsToProcess(Set<String> onlineSegments) {
    Set<String> segmentsToProcess = new HashSet<>(onlineSegments);
    segmentsToProcess.removeAll(_segmentsToRemove);
    return segmentsToProcess;
  }
}
