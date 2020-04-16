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
package org.apache.pinot.plugin.filesystem;

import com.google.common.collect.ImmutableList;
import org.apache.commons.configuration.Configuration;
import org.apache.pinot.spi.filesystem.PinotFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.internal.resource.S3BucketResource;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static joptsimple.internal.Strings.isNullOrEmpty;
import static org.glassfish.jersey.internal.guava.Preconditions.checkArgument;


public class S3PinotFS extends PinotFS {
  public static final String ACCESS_KEY = "accessKey";
  public static final String SECRET_KEY = "secretKey";
  public static final String REGION = "region";

  private static final Logger LOGGER = LoggerFactory.getLogger(S3PinotFS.class);
  private static final String DELIMITER = "/";
  private S3Client s3Client;

  @Override
  public void init(Configuration config) {
    checkArgument(!isNullOrEmpty(config.getString(ACCESS_KEY)));
    checkArgument(!isNullOrEmpty(config.getString(SECRET_KEY)));
    checkArgument(!isNullOrEmpty(config.getString(REGION)));

    String accessKey = config.getString(ACCESS_KEY);
    String secretKey = config.getString(SECRET_KEY);
    String region  = config.getString(REGION);
    try {
      AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(accessKey, secretKey);
      s3Client = S3Client.builder().region(Region.of(region))
          .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials)).build();
    } catch (S3Exception e) {
      throw new RuntimeException("Could not initialize S3PinotFS", e);
    }
  }

  private HeadObjectResponse getS3ObjectMetadata(URI uri)
      throws IOException {
    URI base = getBase(uri);
    String path = sanitizePath(base.relativize(uri).getPath());
    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder().bucket(uri.getHost()).key(path).build();

    return s3Client.headObject(headObjectRequest);
  }

  private boolean isPathTerminatedByDelimiter(URI uri) {
    return uri.getPath().endsWith(DELIMITER);
  }

  private String normalizeToDirectoryPrefix(URI uri)
      throws IOException {
    requireNonNull(uri, "uri is null");
    URI strippedUri = getBase(uri).relativize(uri);
    if (isPathTerminatedByDelimiter(strippedUri)) {
      return sanitizePath(strippedUri.getPath());
    }
    return sanitizePath(strippedUri.getPath() + DELIMITER);
  }

  private URI normalizeToDirectoryUri(URI uri)
      throws IOException {
    if (isPathTerminatedByDelimiter(uri)) {
      return uri;
    }
    try {
      return new URI(uri.getScheme(), uri.getHost(), sanitizePath(uri.getPath() + DELIMITER), null);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private String sanitizePath(String path) {
    path = path.replaceAll(DELIMITER + "+", DELIMITER);
    if (path.startsWith(DELIMITER) && !path.equals(DELIMITER)) {
      path = path.substring(1);
    }
    return path;
  }

  private URI getBase(URI uri)
      throws IOException {
    try {
      return new URI(uri.getScheme(), uri.getHost(), null, null);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private boolean existsFile(URI uri)
      throws IOException {
    try {
      URI base = getBase(uri);
      String path = sanitizePath(base.relativize(uri).getPath());
      HeadObjectRequest headObjectRequest = HeadObjectRequest.builder().bucket(uri.getHost()).key(path).build();

      s3Client.headObject(headObjectRequest);
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      throw new IOException(e);
    }
  }

  private boolean isEmptyDirectory(URI uri)
      throws IOException {
    if (!isDirectory(uri)) {
      return false;
    }
    String prefix = normalizeToDirectoryPrefix(uri);
    boolean isEmpty = true;
    ListObjectsV2Response listObjectsV2Response;
    ListObjectsV2Request.Builder listObjectsV2RequestBuilder = ListObjectsV2Request.builder().bucket(uri.getHost());

    if (prefix.equals(DELIMITER)) {
      ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.build();
      listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
    } else {
      ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.prefix(prefix).build();
      listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
    }
    for (S3Object s3Object : listObjectsV2Response.contents()) {
      if (s3Object.key().equals(prefix)) {
        continue;
      } else {
        isEmpty = false;
        break;
      }
    }
    return isEmpty;
  }

  private boolean copyFile(URI srcUri, URI dstUri)
      throws IOException {
    try {
      String encodedUrl = null;
      try {
        encodedUrl = URLEncoder.encode(srcUri.getHost() + srcUri.getPath(), StandardCharsets.UTF_8.toString());
      } catch (UnsupportedEncodingException e) {
        LOGGER.info("URL could not be encoded: {}", e.getMessage());
      }

      String dstPath = sanitizePath(dstUri.getPath());
      CopyObjectRequest copyReq =
          CopyObjectRequest.builder().copySource(encodedUrl).destinationBucket(dstUri.getHost()).destinationKey(dstPath)
              .build();

      CopyObjectResponse copyObjectResponse = s3Client.copyObject(copyReq);
      return copyObjectResponse.sdkHttpResponse().isSuccessful();
    } catch (S3Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean mkdir(URI uri)
      throws IOException {
    LOGGER.info("mkdir {}", uri);
    try {
      requireNonNull(uri, "uri is null");
      String path = normalizeToDirectoryPrefix(uri);
      // Bucket root directory already exists and cannot be created
      if (path.equals(DELIMITER)) {
        return true;
      }

      PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(uri.getHost()).key(path).build();

      PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(new byte[0]));

      return putObjectResponse.sdkHttpResponse().isSuccessful();
    } catch (Throwable t) {
      throw new IOException(t);
    }
  }

  @Override
  public boolean delete(URI segmentUri, boolean forceDelete)
      throws IOException {
    LOGGER.info("Deleting uri {} force {}", segmentUri, forceDelete);
    try {
      if (isDirectory(segmentUri)) {
        if (!forceDelete) {
          checkState(isEmptyDirectory(segmentUri), "ForceDelete flag is not set and directory '%s' is not empty",
              segmentUri);
        }
        String prefix = normalizeToDirectoryPrefix(segmentUri);
        ListObjectsV2Response listObjectsV2Response;
        ListObjectsV2Request.Builder listObjectsV2RequestBuilder =
            ListObjectsV2Request.builder().bucket(segmentUri.getHost());

        if (prefix.equals(DELIMITER)) {
          ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.build();
          listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
        } else {
          ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.prefix(prefix).build();
          listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
        }
        boolean deleteSucceeded = true;
        for (S3Object s3Object : listObjectsV2Response.contents()) {
          DeleteObjectRequest deleteObjectRequest =
              DeleteObjectRequest.builder().bucket(segmentUri.getHost()).key(s3Object.key()).build();

          DeleteObjectResponse deleteObjectResponse = s3Client.deleteObject(deleteObjectRequest);

          deleteSucceeded &= deleteObjectResponse.sdkHttpResponse().isSuccessful();
        }
        return deleteSucceeded;
      } else {
        String prefix = sanitizePath(segmentUri.getPath());
        DeleteObjectRequest deleteObjectRequest =
            DeleteObjectRequest.builder().bucket(segmentUri.getHost()).key(prefix).build();

        DeleteObjectResponse deleteObjectResponse = s3Client.deleteObject(deleteObjectRequest);

        return deleteObjectResponse.sdkHttpResponse().isSuccessful();
      }
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      throw e;
    } catch (Throwable t) {
      throw new IOException(t);
    }
  }

  @Override
  public boolean doMove(URI srcUri, URI dstUri)
      throws IOException {
    if (copy(srcUri, dstUri)) {
      return delete(srcUri, true);
    }
    return false;
  }

  @Override
  public boolean copy(URI srcUri, URI dstUri)
      throws IOException {
    LOGGER.info("Copying uri {} to uri {}", srcUri, dstUri);
    checkState(exists(srcUri), "Source URI '%s' does not exist", srcUri);
    if (srcUri.equals(dstUri)) {
      return true;
    }
    if (!isDirectory(srcUri)) {
      delete(dstUri, true);
      return copyFile(srcUri, dstUri);
    }
    dstUri = normalizeToDirectoryUri(dstUri);
    ImmutableList.Builder<URI> builder = ImmutableList.builder();
    Path srcPath = Paths.get(srcUri.getPath());
    try {
      boolean copySucceeded = true;
      for (String directoryEntry : listFiles(srcUri, true)) {
        URI src = new URI(srcUri.getScheme(), srcUri.getHost(), directoryEntry, null);
        String relativeSrcPath = srcPath.relativize(Paths.get(directoryEntry)).toString();
        String dstPath = dstUri.resolve(relativeSrcPath).getPath();
        URI dst = new URI(dstUri.getScheme(), dstUri.getHost(), dstPath, null);
        copySucceeded &= copyFile(src, dst);
      }
      return copySucceeded;
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean exists(URI fileUri)
      throws IOException {
    try {
      if (isDirectory(fileUri)) {
        return true;
      }
      if (isPathTerminatedByDelimiter(fileUri)) {
        return false;
      }
      return existsFile(fileUri);
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  @Override
  public long length(URI fileUri)
      throws IOException {
    try {
      checkState(!isPathTerminatedByDelimiter(fileUri), "URI is a directory");
      HeadObjectResponse s3ObjectMetadata = getS3ObjectMetadata(fileUri);
      checkState((s3ObjectMetadata != null), "File '%s' does not exist", fileUri);
      if (s3ObjectMetadata.contentLength() == null) {
        return 0;
      }
      return s3ObjectMetadata.contentLength();
    } catch (Throwable t) {
      throw new IOException(t);
    }
  }

  @Override
  public String[] listFiles(URI fileUri, boolean recursive)
      throws IOException {
    try {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      String prefix = normalizeToDirectoryPrefix(fileUri);
      ListObjectsV2Response listObjectsV2Response;
      ListObjectsV2Request.Builder listObjectsV2RequestBuilder =
          ListObjectsV2Request.builder().bucket(fileUri.getHost()).prefix(prefix);

      if (recursive) {
        ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.build();
        listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
      } else {
        ListObjectsV2Request listObjectsV2Request = listObjectsV2RequestBuilder.delimiter(DELIMITER).build();
        listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
      }

      listObjectsV2Response.contents().stream().forEach(object -> {
        //Only add files and not directories
        if (!object.key().equals(fileUri.getPath()) && !object.key().endsWith(DELIMITER)) {
          builder.add(object.key());
        }
      });
      return builder.build().toArray(new String[0]);
    } catch (Throwable t) {
      throw new IOException(t);
    }
  }

  @Override
  public void copyToLocalFile(URI srcUri, File dstFile)
      throws Exception {
    LOGGER.info("Copy {} to local {}", srcUri, dstFile.getAbsolutePath());
    URI base = getBase(srcUri);
    String prefix = sanitizePath(base.relativize(srcUri).getPath());
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(srcUri.getHost()).key(prefix).build();

    s3Client.getObject(getObjectRequest, ResponseTransformer.toFile(dstFile));
  }

  @Override
  public void copyFromLocalFile(File srcFile, URI dstUri)
      throws Exception {
    LOGGER.info("Copy {} from local to {}", srcFile.getAbsolutePath(), dstUri);
    URI base = getBase(dstUri);
    String prefix = sanitizePath(base.relativize(dstUri).getPath());
    PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(dstUri.getHost()).key(prefix).build();

    PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest, srcFile.toPath());
  }

  @Override
  public boolean isDirectory(URI uri) {
    try {
      String prefix = sanitizePath(uri.getPath());
      if (prefix.equals(DELIMITER)) {
        return true;
      }
      HeadObjectResponse s3ObjectMetadata;
      HeadObjectRequest headObjectRequest = HeadObjectRequest.builder().bucket(uri.getHost()).key(prefix).build();
      s3ObjectMetadata = s3Client.headObject(headObjectRequest);
      return s3ObjectMetadata.contentType().contentEquals("application/x-directory");
    } catch (NoSuchKeyException e) {
      LOGGER.error("Could not get directory entry for {}", uri);
      return false;
    }
  }

  @Override
  public long lastModified(URI uri)
      throws IOException {
    return getS3ObjectMetadata(uri).lastModified().toEpochMilli();
  }

  @Override
  public boolean touch(URI uri)
      throws IOException {
    try {
      HeadObjectResponse s3ObjectMetadata = getS3ObjectMetadata(uri);
      String encodedUrl = null;
      try {
        encodedUrl = URLEncoder.encode(uri.getHost() + uri.getPath(), StandardCharsets.UTF_8.toString());
      } catch (UnsupportedEncodingException e) {
        LOGGER.info("URL could not be encoded: {}", e.getMessage());
      }

      String path = sanitizePath(uri.getPath());
      Map<String, String> mp = new HashMap<>();
      mp.put("lastModified", String.valueOf(System.currentTimeMillis()));
      CopyObjectRequest request =
          CopyObjectRequest.builder().copySource(encodedUrl).destinationBucket(uri.getHost()).destinationKey(path)
              .metadata(mp).metadataDirective(MetadataDirective.REPLACE).build();

      s3Client.copyObject(request);
      long newUpdateTime = getS3ObjectMetadata(uri).lastModified().toEpochMilli();
      return newUpdateTime > s3ObjectMetadata.lastModified().toEpochMilli();
    } catch (NoSuchKeyException e) {
      String path = sanitizePath(uri.getPath());
      s3Client.putObject(PutObjectRequest.builder().bucket(uri.getHost()).key(path).build(),
          RequestBody.fromBytes(new byte[0]));
      return true;
    } catch (S3Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public InputStream open(URI uri)
      throws IOException {
    try {
      String path = sanitizePath(uri.getPath());
      GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(uri.getHost()).key(path).build();

      ResponseBytes responseBytes = s3Client.getObjectAsBytes(getObjectRequest);
      return responseBytes.asInputStream();
    } catch (S3Exception e) {
      throw e;
    }
  }

  @Override
  public void close()
      throws IOException {
    super.close();
  }
}
