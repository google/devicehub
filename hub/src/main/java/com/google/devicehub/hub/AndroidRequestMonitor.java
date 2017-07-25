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

import com.google.devicehub.proto.CustomCodeEndpoint;
import com.google.devicehub.proto.DeviceResponse;
import com.google.devicehub.proto.SmartDeviceRequest;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Class to monitor the Android request queue and send the new request to the connected device via
 * the response observer
 */
public class AndroidRequestMonitor extends DeviceRequestMonitor {

  private static final Logger logger = HubLogger.getLogger(AndroidRequestMonitor.class);

  private final StreamObserver<SmartDeviceRequest> responseObserver;
  private final ConcurrentHashMap<String, BlockingQueue<DeviceResponse>> responseQueueMap;

  public AndroidRequestMonitor(
      BlockingQueue<DeviceRequestInfo> requestQueue,
      StreamObserver<SmartDeviceRequest> responseObserver,
      ConcurrentHashMap<String, BlockingQueue<DeviceResponse>> responseQueueMap) {
    super(requestQueue);
    this.responseObserver = responseObserver;
    this.responseQueueMap = responseQueueMap;
  }

  public StreamObserver<SmartDeviceRequest> getResponseObserver() {
    return responseObserver;
  }

  @Override
  protected void processRequest(DeviceRequestInfo deviceRequestInfo) {
    CustomCodeEndpoint customCodeEndpoint = deviceRequestInfo.getEndpoint();
    logger.info("Processing custom code request: " + customCodeEndpoint.getTargetName());
    String uuid = UUID.randomUUID().toString();
    responseQueueMap.put(uuid, deviceRequestInfo.getResponseQueue());
    responseObserver.onNext(
        SmartDeviceRequest.newBuilder().setEndpoint(customCodeEndpoint).setUuid(uuid).build());
    logger.info("Sent custom code request to device");
  }
}
