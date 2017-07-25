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

package com.google.devicehub.client.android;

import com.google.devicehub.proto.DeviceHubGrpc;
import com.google.devicehub.proto.DeviceRequest;
import com.google.devicehub.proto.DeviceResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The client to be used by the Android device to communicate with device hub server to send
 * requests to other devices and get the responses back.
 */
public class AndroidDeviceHubClient {

  private static final Logger logger = Logger.getLogger(AndroidDeviceHubClient.class.getName());
  private static final String DEFAULT_HOST = "localhost";
  private static final int SHUTDOWN_WAIT_SECS = 5;

  private final int hubPort;
  private final ExecutorService executorService;

  public AndroidDeviceHubClient(int hubPort) {
    this.hubPort = hubPort;
    this.executorService = Executors.newCachedThreadPool();
  }

  /**
   * Send a device request to the device hub which will then route to the device
   *
   * @param request the device request
   * @return the response from the device
   */
  public DeviceResponse sendDeviceRequest(DeviceRequest request) {
    ManagedChannel channel = null;
    try {
      logger.info(
          String.format(
              "Sending device request: %s %s",
              request.getDeviceFilter(), request.getCustomCodeEndpoint().getTargetName()));
      // The gRPC channel used to communicate with the server
      channel =
          ManagedChannelBuilder.forAddress(DEFAULT_HOST, hubPort)
              // Channels are secure by default (via SSL/TLS), disable TLS to avoid needing
              // certificates.
              .usePlaintext(true)
              .build();
      DeviceResponse response = DeviceHubGrpc.newBlockingStub(channel).sendDeviceRequest(request);
      logger.info("Received response from the server: " + response.getStatus());

      return response;
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
   * @param request the device request to send
   * @return the Future instance that holds the response
   */
  public Future<DeviceResponse> sendDeviceRequestAsync(DeviceRequest request) {
    return executorService.submit(
        new Callable<DeviceResponse>() {
          @Override
          public DeviceResponse call() {
            return sendDeviceRequest(request);
          }
        });
  }
}
