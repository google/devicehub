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
import com.google.common.base.Objects;
import com.google.devicehub.proto.DeviceRequestStatusEnum;
import com.google.devicehub.proto.DeviceResponse;
import java.util.Arrays;

/** The response from the device */
public class ClientDeviceResponse {

  /** The status of the client request */
  public enum Status {
    SUCCESS,
    FAILURE,
  }

  // The status of the request
  private final Status status;

  private final String errorMessage;

  // The response data blob returned from the device, it is up to the test and the target method to
  // interpret the content it
  private final byte[] responseData;

  @VisibleForTesting
  ClientDeviceResponse(Status status, String errorMessage, byte[] responseData) {
    this.status = status;
    this.errorMessage = errorMessage;
    this.responseData = responseData;
  }

  public Status getStatus() {
    return status;
  }

  public byte[] getResponseData() {
    return responseData;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public static ClientDeviceResponse fromDeviceResponse(DeviceResponse response) {
    Status status =
        response.getStatus() == DeviceRequestStatusEnum.SUCCESS ? Status.SUCCESS : Status.FAILURE;
    return new ClientDeviceResponse(
        status, response.getErrorMessage(), response.getResponseData().toByteArray());
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    String lineSeparator = System.getProperty("line.separator");

    builder.append("Status: ").append(status).append(lineSeparator);
    builder.append("Error message: ").append(errorMessage).append(lineSeparator);
    if (responseData != null) {
      builder.append("Response data size: ").append(responseData.length).append(lineSeparator);
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
    ClientDeviceResponse that = (ClientDeviceResponse) o;
    return status == that.status
        && Objects.equal(errorMessage, that.errorMessage)
        && Arrays.equals(responseData, that.responseData);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status, errorMessage, Arrays.hashCode(responseData));
  }
}
