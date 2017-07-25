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

import com.google.common.base.Objects;
import java.util.Arrays;

/** A request sent from the test to the device */
public class ClientDeviceRequest {

  // The filter of the target devices
  private final ClientDeviceFilter clientDeviceFilter;

  // The target method name to be triggered on the device
  private final String targetName;

  // The requestData sent to the target method, it is up to the test and the target method to
  // interpret the content of the requestData
  private final byte[] requestData;

  public ClientDeviceRequest(
      ClientDeviceFilter deviceFilter, String targetName, byte[] requestData) {
    this.clientDeviceFilter = deviceFilter;
    this.targetName = targetName;
    this.requestData = requestData;
  }

  public ClientDeviceFilter getClientDeviceFilter() {
    return clientDeviceFilter;
  }

  public String getTargetName() {
    return targetName;
  }

  public byte[] getRequestData() {
    return requestData;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    String lineSeparator = System.getProperty("line.separator");

    builder.append("Client device filter: ").append(clientDeviceFilter).append(lineSeparator);
    builder.append("Target name: ").append(targetName).append(lineSeparator);
    if (requestData != null) {
      builder.append("Request data size: ").append(requestData.length).append(lineSeparator);
    }

    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClientDeviceRequest that = (ClientDeviceRequest) o;
    return Objects.equal(clientDeviceFilter, that.clientDeviceFilter)
        && Objects.equal(targetName, that.targetName)
        && Arrays.equals(requestData, that.requestData);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(clientDeviceFilter, targetName, Arrays.hashCode(requestData));
  }
}
