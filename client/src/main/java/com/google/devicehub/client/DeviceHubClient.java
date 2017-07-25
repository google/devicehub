// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devicehub.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.devicehub.proto.CustomCodeEndpoint;
import com.google.devicehub.proto.DeviceRequest;
import com.google.devicehub.proto.DeviceResponse;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client to be used by test case to communicate with device hub server to send requests to the
 * devices and get the responses back.
 */
public class DeviceHubClient {

  private static final Logger logger = Logger.getLogger(DeviceHubClient.class.getName());
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_PORT = "50008";
  private static final String HUB_PORT_PROPERTY_KEY = "hub_port";
  private static final int SHUTDOWN_WAIT_SECS = 5;

  private final HubConnectionProvider hubConnectionProvider;
  private final ExecutorService executorService;
  private final int hubPort;

  public DeviceHubClient() {
    this(new GrpcHubConnectionProvider());
  }
  
  /** Constructor for testing purpose so that some instances can be injected */
  @VisibleForTesting
  DeviceHubClient(HubConnectionProvider hubConnectionProvider) {
    this.hubConnectionProvider = hubConnectionProvider;
    this.executorService = Executors.newCachedThreadPool();
    this.hubPort = Integer.valueOf(System.getProperty(HUB_PORT_PROPERTY_KEY, DEFAULT_PORT));
  }

  /**
   * Send a device request to the device hub which will then route to the device
   *
   * @param clientDeviceRequest the device request
   * @return the response from the device
   */
  public ClientDeviceResponse sendDeviceRequest(ClientDeviceRequest clientDeviceRequest) {
    CustomCodeEndpoint.Builder builder = CustomCodeEndpoint.newBuilder();
    builder.setTargetName(clientDeviceRequest.getTargetName());
    if (clientDeviceRequest.getRequestData() != null) {
      builder.setCustomCodeParms(ByteString.copyFrom(clientDeviceRequest.getRequestData()));
    }

    DeviceRequest request =
        DeviceRequest.newBuilder()
            .setDeviceFilter(clientDeviceRequest.getClientDeviceFilter().toDeviceFilter())
            .setCustomCodeEndpoint(builder.build())
            .build();

    ManagedChannel channel = null;
    try {
      logger.info("Sending device request: " + clientDeviceRequest);
      // The gRPC channel used to communicate with the server
      channel = hubConnectionProvider.getManagedChannel(DEFAULT_HOST, hubPort);
      DeviceResponse response = hubConnectionProvider.sendDeviceRequest(channel, request);
      logger.info("Received response from the server: " + response);

      return ClientDeviceResponse.fromDeviceResponse(response);
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "Failed to send device request: ", e);
      throw e;
    } finally {
      try {
        channel.shutdown().awaitTermination(SHUTDOWN_WAIT_SECS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "Failed to shut down the communication channel: ", e);
      }
    }
  }

  /**
   * Send the device request asynchronously
   *
   * @param clientDeviceRequest the device request to send
   * @return the Future instance that holds the response
   */
  public Future<ClientDeviceResponse> sendDeviceRequestAsync(
      ClientDeviceRequest clientDeviceRequest) {

    return executorService.submit(
        new Callable<ClientDeviceResponse>() {
          @Override
          public ClientDeviceResponse call() {
            return sendDeviceRequest(clientDeviceRequest);
          }
        });
  }
}
