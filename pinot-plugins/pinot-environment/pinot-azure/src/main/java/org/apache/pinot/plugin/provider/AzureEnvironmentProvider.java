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
package org.apache.pinot.plugin.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.pinot.spi.env.PinotConfiguration;
import org.apache.pinot.spi.environmentprovider.PinotEnvironmentProvider;
import org.apache.pinot.spi.utils.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Azure Environment Provider used to retrieve azure cloud specific instance configuration.
 */
public class AzureEnvironmentProvider implements PinotEnvironmentProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureEnvironmentProvider.class);

  protected static final String MAX_RETRY = "maxRetry";
  protected static final String IMDS_ENDPOINT = "imdsEndpoint";
  protected static final String CONNECTION_TIMEOUT = "connectionTimeout";
  protected static final String REQUEST_TIMEOUT = "requestTimeout";
  private static final String COMPUTE = "compute";
  private static final String METADATA = "Metadata";
  private static final String PLATFORM_FAULT_DOMAIN = "platformFaultDomain";
  private int _maxRetry;
  private String _imdsEndpoint;
  private CloseableHttpClient _closeableHttpClient;

  public AzureEnvironmentProvider() {
  }

  public void init(PinotConfiguration pinotConfiguration) {
    Preconditions.checkArgument(0 < Integer.parseInt(pinotConfiguration.getProperty(MAX_RETRY)),
         "[AzureEnvironmentProvider]: " + MAX_RETRY + " cannot be less than or equal to 0");
    Preconditions.checkArgument(!StringUtils.isBlank(pinotConfiguration.getProperty(IMDS_ENDPOINT)),
        "[AzureEnvironmentProvider]: " + IMDS_ENDPOINT + " should not be null or empty");

    _maxRetry = Integer.parseInt(pinotConfiguration.getProperty(MAX_RETRY));
    _imdsEndpoint = pinotConfiguration.getProperty(IMDS_ENDPOINT);
    int connectionTimeout = Integer.parseInt(pinotConfiguration.getProperty(CONNECTION_TIMEOUT));
    int requestTimeout = Integer.parseInt(pinotConfiguration.getProperty(REQUEST_TIMEOUT));

    final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(requestTimeout)
        .build();

    final HttpRequestRetryHandler httpRequestRetryHandler = (iOException, executionCount, httpContext) ->
        !(executionCount >= _maxRetry
            || iOException instanceof InterruptedIOException
            || iOException instanceof UnknownHostException
            || iOException instanceof SSLException
            || HttpClientContext.adapt(httpContext).getRequest() instanceof HttpEntityEnclosingRequest);

    _closeableHttpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).setRetryHandler(httpRequestRetryHandler).build();
  }

  // Constructor for test purposes.
  @VisibleForTesting
  public AzureEnvironmentProvider(int maxRetry, String imdsEndpoint, CloseableHttpClient closeableHttpClient) {
    _maxRetry = maxRetry;
    _imdsEndpoint = imdsEndpoint;
    _closeableHttpClient = Preconditions.checkNotNull(closeableHttpClient,
        "[AzureEnvironmentProvider]: Closeable Http Client cannot be null");
  }

  /**
   *
   * Method for constructing custom pinot configuration map used by the HelixServerStarter to update
   * zookeeper node with custom instance configs.
   * @return custom pinot configuration map
   */
  @Override
  public Map<String, String> getEnvironment() {
    Map<String, String> customPinotConfiguration = new HashMap<>();

    String failureDomain = getFailureDomain();
    if (StringUtils.isBlank(failureDomain)) {
      LOGGER.error("[AzureEnvironmentProvider]: No failure domain information retrieved for given server instance.");
      throw new RuntimeException("No failure domain information retrieved for given server instance.");
    }
    // Populate failure domain information
    customPinotConfiguration.put(CommonConstants.INSTANCE_FAILURE_DOMAIN,  failureDomain);

    return customPinotConfiguration;
  }

  // Utility used to query the azure instance metadata service (Azure IMDS) to fetch the fault/failure domain information.
  @VisibleForTesting
  protected final String getFailureDomain() {
    final String responsePayload = getAzureInstanceMetadata();

    // For a sample response payload,
    // check https://docs.microsoft.com/en-us/azure/virtual-machines/windows/instance-metadata-service?tabs=linux
    final ObjectMapper objectMapper = new ObjectMapper();
    try {
      final JsonNode jsonNode = objectMapper.readTree(responsePayload);
      final JsonNode computeNode = jsonNode.path(COMPUTE);

      if (computeNode.isMissingNode()) {
        throw new RuntimeException(
            "Compute node is missing in the payload. Cannot retrieve Failure Domain Information");
      }
      final JsonNode platformFailureDomainNode = computeNode.path(PLATFORM_FAULT_DOMAIN);
      if (platformFailureDomainNode.isMissingNode() || !platformFailureDomainNode.isTextual()) {
        throw new RuntimeException("Json node platformFaultDomain is missing or is invalid.");
      }
      return platformFailureDomainNode.textValue();
    } catch (IOException ex) {
      throw new RuntimeException(
          String.format(
              "Errors when parsing response payload from Azure Instance Metadata Service: %s", responsePayload), ex);
    }
  }

  // Utility used to construct the HTTP Request and fetch corresponding response entity.
  @VisibleForTesting
  private String getAzureInstanceMetadata() {
    HttpGet httpGet = new HttpGet(_imdsEndpoint);
    httpGet.setHeader(METADATA, Boolean.TRUE.toString());

    try {
      final CloseableHttpResponse closeableHttpResponse = _closeableHttpClient.execute(httpGet);
      if (closeableHttpResponse == null) {
        throw new RuntimeException("Response is null. Please verify the imds endpoint");
      }
      final StatusLine statusLine = closeableHttpResponse.getStatusLine();
      final int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK) {
        final String errorMsg = String.format(
            "Failed to retrieve azure instance metadata. Response Status code: %s", statusCode);
        throw new RuntimeException(errorMsg);
      }
      return EntityUtils.toString(closeableHttpResponse.getEntity());
    } catch (IOException ex) {
      throw new RuntimeException(
          String.format("Failed to retrieve metadata from Azure Instance Metadata Service %s", _imdsEndpoint), ex);
    }
  }
}
