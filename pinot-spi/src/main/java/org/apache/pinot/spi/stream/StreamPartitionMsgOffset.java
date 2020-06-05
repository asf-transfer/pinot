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
package org.apache.pinot.spi.stream;

import org.apache.pinot.spi.annotations.InterfaceStability;

/**
 * This is a class for now (so we can make one round of changes to the code)
 * It will evolve to an interface later with different implementations for the
 * streams. Each stream needs to provide its own serde of an offset.
 *
 * For now, we will take toString as a serializer.
 * Must be thread-safe for multiple readers and one writer. Readers could get the offset
 * and the writer can update it.
 */
@InterfaceStability.Evolving
public interface StreamPartitionMsgOffset extends Comparable {

  int compareTo(Object other);

  /**
   *  A serialized representation of the offset object as a String
   * @return
   */
  String toString();
}
