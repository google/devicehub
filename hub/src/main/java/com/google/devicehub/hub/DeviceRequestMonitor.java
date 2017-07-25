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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Base class of all the device request monitor classes which monitor the request queues and send
 * the new requests to the connected devices
 */
public abstract class DeviceRequestMonitor implements Runnable {

  private static final Logger logger = HubLogger.getLogger(DeviceRequestMonitor.class);

  private final BlockingQueue<DeviceRequestInfo> requestQueue;

  private volatile boolean finished = false;

  public DeviceRequestMonitor(BlockingQueue<DeviceRequestInfo> requestQueue) {
    this.requestQueue = requestQueue;
  }

  public void finished() {
    finished = true;
  }

  public BlockingQueue<DeviceRequestInfo> getRequestQueue() {
    return requestQueue;
  }

  @Override
  public void run() {

    logger.info("Starting to poll next request from the device request queue");

    while (!finished) {
      try {

        DeviceRequestInfo deviceRequestInfo = requestQueue.poll(1, TimeUnit.SECONDS);
        if (deviceRequestInfo == null) {
          continue;
        }

        processRequest(deviceRequestInfo);

      } catch (InterruptedException e) {
        logger.warning("Failed to process device request, exiting: " + e.getMessage());
        break;
      }
    }
    logger.info("RequestProcessor finished, die gracefully");
  }

  /**
   * Process the received request
   *
   * @param deviceRequestInfo the received device request
   */
  protected abstract void processRequest(DeviceRequestInfo deviceRequestInfo);
}
