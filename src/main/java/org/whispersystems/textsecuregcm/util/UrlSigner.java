/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.util;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.whispersystems.textsecuregcm.configuration.S3Configuration;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;

import java.net.URL;
import java.util.Date;

public class UrlSigner {

  private static final long   DURATION = 60 * 60 * 1000;

  private final AmazonS3 s3client;
  private final String bucket;

  public UrlSigner(S3Configuration config) {
    AWSCredentials credentials = new BasicAWSCredentials(config.getAccessKey(), config.getAccessSecret());
    this.bucket      = config.getAttachmentsBucket();

    AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
    if (config.getEndpoint() != null) {
      // https://docs.minio.io/docs/how-to-use-aws-sdk-for-java-with-minio-server.html
      ClientConfiguration clientConfiguration = new ClientConfiguration();
      clientConfiguration.setSignerOverride("AWSS3V4SignerType");
      this.s3client = AmazonS3ClientBuilder.standard()
                                           .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpoint(),
                                                                                                                 config.getRegion()))
                                           .withPathStyleAccessEnabled(true)
                                           .withClientConfiguration(clientConfiguration)
                                           .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                           .build();
    } else {
        this.s3client = AmazonS3Client.builder().withCredentials(credentialsProvider)
                                                .withRegion(config.getRegion())
                                                .enableAccelerateMode()
                                                .build();
    }
  }

  public URL getPreSignedUrl(long attachmentId, HttpMethod method) {
    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, String.valueOf(attachmentId), method);
    
    request.setExpiration(new Date(System.currentTimeMillis() + DURATION));
    request.setContentType("application/octet-stream");

    return s3client.generatePresignedUrl(request);
  }
}
