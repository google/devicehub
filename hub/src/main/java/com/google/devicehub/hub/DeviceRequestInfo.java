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

import com.google.common.base.Objects;
import com.google.devicehub.proto.CustomCodeEndpoint;
import com.google.devicehub.proto.DeviceResponse;
import java.util.concurrent.BlockingQueue;

/**
 * The device request information which include the custom code endpoint and the corresponding
 * response queue
 */
public class DeviceRequestInfo {

  private CustomCodeEndpoint endpoint;
  private BlockingQueue<DeviceResponse> responseQueue;

  public DeviceRequestInfo(
      CustomCodeEndpoint endpoint, BlockingQueue<DeviceResponse> responseQueue) {
    this.endpoint = endpoint;
    this.responseQueue = responseQueue;
  }

  public CustomCodeEndpoint getEndpoint() {
    return endpoint;
  }

  public BlockingQueue<DeviceResponse> getResponseQueue() {
    return responseQueue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceRequestInfo that = (DeviceRequestInfo) o;
    return Objects.equal(endpoint, that.endpoint)
        && Objects.equal(responseQueue, that.responseQueue);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(endpoint, responseQueue);
  }
}
