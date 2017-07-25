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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.devicehub.proto.DeviceFilter;
import com.google.devicehub.proto.DeviceTypeEnum;

/**
 * A device filter used by the client to find the right devices, the reason of using this instead of
 * the proto definition directly is to hide the test cases from the implementation details of the
 * hub so that it can replaced with new data definition without affecting the tests.
 */
public class ClientDeviceFilter {

  /** Device deviceType enum */
  public enum DeviceType {
    // Map this Device Type to a proto DeviceType
    DEVICE_TYPE_UNSPECIFIED(DeviceTypeEnum.DEVICE_TYPE_UNSPECIFIED),
    ANDROID(DeviceTypeEnum.ANDROID),
    IOS(DeviceTypeEnum.IOS),
    ROBOTIC_DEVICE(DeviceTypeEnum.ROBOTIC_DEVICE),
    SPIRENT_RPS(DeviceTypeEnum.SPIRENT_RPS);

    private final DeviceTypeEnum protoDeviceType;

    private DeviceType(DeviceTypeEnum protoDeviceType) {
      this.protoDeviceType = protoDeviceType;
    }

    public DeviceTypeEnum toProtoDeviceType() {
      return protoDeviceType;
    }
  };

  // Device Type
  private final DeviceType deviceType;
  // Device ID
  private final String deviceId;
  // Device label
  private final String deviceLabel;

  private ClientDeviceFilter(DeviceType deviceType, String deviceId, String deviceLabel) {
    this.deviceType = deviceType;
    this.deviceId = deviceId;
    this.deviceLabel = deviceLabel;
  }

  public String getDeviceId() {
    return deviceId;
  }

  /**
   * Converts this to a DeviceFilter object
   *
   * @return the DeviceFilter object converted
   */
  public DeviceFilter toDeviceFilter() {
    DeviceFilter deviceFilter =
        DeviceFilter.newBuilder()
            .setDeviceId(this.deviceId == null ? "" : this.deviceId)
            .setDeviceLabel(this.deviceLabel)
            .setDeviceType(this.deviceType.toProtoDeviceType())
            .build();

    return deviceFilter;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    String lineSeparator = System.getProperty("line.separator");

    builder.append("Device type: ").append(deviceType).append(lineSeparator);
    builder.append("Device id: ").append(deviceId).append(lineSeparator);
    builder.append("Device label: ").append(deviceLabel).append(lineSeparator);

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
    ClientDeviceFilter that = (ClientDeviceFilter) o;
    return Objects.equal(this.deviceType, that.deviceType)
        && Objects.equal(this.deviceId, that.deviceId)
        && Objects.equal(this.deviceLabel, that.deviceLabel);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(deviceType, deviceId, deviceLabel);
  }

  /** Returns a new instance of Builder, the device type is mandatory */
  public static Builder newBuilder(DeviceType deviceType) {
    return new Builder(deviceType);
  }

  /** Builder that can be used to obtain new instances of ClientDeviceFilter */
  public static class Builder {

    // Device Type
    private DeviceType deviceType = DeviceType.DEVICE_TYPE_UNSPECIFIED;
    // Device ID
    private String deviceId = "";
    // Device label
    private String deviceLabel = "";

    private Builder(DeviceType deviceType) {
      this.deviceType = deviceType;
    }

    public Builder setDeviceId(String deviceId) {
      this.deviceId = deviceId;
      return this;
    }

    public Builder setDeviceLabel(String deviceLabel) {
      this.deviceLabel = deviceLabel;
      return this;
    }

    public ClientDeviceFilter build() {
      Preconditions.checkState(
          (!Strings.isNullOrEmpty(deviceId) || !Strings.isNullOrEmpty(deviceLabel)),
          "Either device id or device label should be set");
      return new ClientDeviceFilter(deviceType, deviceId, deviceLabel);
    }
  }
}
