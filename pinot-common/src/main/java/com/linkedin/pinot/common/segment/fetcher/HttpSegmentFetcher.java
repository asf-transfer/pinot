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
package com.linkedin.pinot.common.segment.fetcher;

import com.linkedin.pinot.common.exception.HttpErrorStatusException;
import com.linkedin.pinot.common.exception.PermanentDownloadException;
import com.linkedin.pinot.common.utils.FileDownloadUtils;
import com.linkedin.pinot.common.utils.retry.RetryPolicies;
import com.linkedin.pinot.common.utils.retry.RetryPolicy;
import java.io.File;
import java.util.concurrent.Callable;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.linkedin.pinot.common.utils.CommonConstants.SegmentFetcher.*;


public class HttpSegmentFetcher implements SegmentFetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpSegmentFetcher.class);
  private int retryCount = RETRY_DEFAULT;
  private int retryWaitMs = RETRY_WAITIME_MS_DEFAULT;

  @Override
  public void init(Configuration configs) {
    retryCount = configs.getInt(RETRY, RETRY_DEFAULT);
    retryWaitMs = configs.getInt(RETRY_WAITIME_MS, RETRY_WAITIME_MS_DEFAULT);
  }

  @Override
  public void fetchSegmentToLocal(final String uri, final File tempFile) throws Exception {
    RetryPolicy policy = RetryPolicies.exponentialBackoffRetryPolicy(retryCount, retryWaitMs, 5);
    policy.attempt(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          int statusCode = FileDownloadUtils.downloadFile(uri, tempFile);
          LOGGER.info("Downloaded file from {} to {}; Length of downloaded file: {}; Response status code: {}", uri,
              tempFile, tempFile.length(), statusCode);
          return true;
        } catch (HttpErrorStatusException e) {
          int statusCode = e.getStatusCode();
          if (statusCode >= 500) {
            LOGGER.error("Failed to download file from {}, might retry", uri, e);
            return false;
          } else {
            LOGGER.error("Failed to download file from {}, won't retry", uri, e);
            throw new PermanentDownloadException(e.getMessage());
          }
        } catch (Exception e) {
          LOGGER.error("Failed to download file from {}, might retry", uri, e);
          return false;
        }
      }
    });
  }
}
