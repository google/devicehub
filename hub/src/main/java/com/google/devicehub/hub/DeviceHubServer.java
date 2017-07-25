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

package com.google.devicehub.hub;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * The device hub server that acts the communication hub between devices and test clients
 */
public class DeviceHubServer {

  private static final Logger logger = HubLogger.getLogger(DeviceHubServer.class);
  // The port on which the server should run
  private static final int PORT = 50008;

  private Server server;
  private DeviceHubImpl deviceHubImpl;

  /**
   * Starts the server
   * @param port the port that the server listens to
   * @throws IOException
   * @throws IllegalStateException
   */
  private void start(int port) throws IOException {

    deviceHubImpl = new DeviceHubImpl();
    try {
      server = ServerBuilder.forPort(port).addService(deviceHubImpl).build().start();
    } catch (IOException | IllegalStateException e) {
      stop();
      throw e;
    }
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                DeviceHubServer.this.stop();
                System.err.println("*** server shut down");
              }
            });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }

    // shutdown the device hub
    if (deviceHubImpl != null) {
      deviceHubImpl.shutdown();
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;

    final DeviceHubServer server = new DeviceHubServer();
    server.start(port);
    server.blockUntilShutdown();
  }
}
