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

package com.google.devicehub.examples;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.devicehub.client.ClientDeviceFilter;
import com.google.devicehub.client.ClientDeviceFilter.DeviceType;
import com.google.devicehub.client.ClientDeviceRequest;
import com.google.devicehub.client.ClientDeviceResponse;
import com.google.devicehub.client.ClientDeviceResponse.Status;
import com.google.devicehub.client.DeviceHubClient;
import com.google.devicehub.examples.proto.TimeRequest;
import com.google.devicehub.examples.proto.TimeResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Simple example to demonstrate how to write test to interact with a Android device. */
@RunWith(JUnit4.class)
public class AndroidDeviceExample {

  private static final Logger logger = Logger.getLogger(AndroidDeviceExample.class.getName());
  private static final String ANDROID_DEVICE_SERIAL_NUM = "HT7320200639";

  private DeviceHubClient client;

  @Before
  public void setup() {
    client = new DeviceHubClient();
  }

  @Test
  public void testGetTimeOnAndroidDevice() {
    TimeRequest timeReq = TimeRequest.newBuilder().setFormat("dd-MMM-yyyy hh:mm:ss").build();
    try {
      // Specify which device the request is sent to
      ClientDeviceFilter androidFilter =
          ClientDeviceFilter.newBuilder(DeviceType.ANDROID)
              .setDeviceId(ANDROID_DEVICE_SERIAL_NUM)
              .build();

      ClientDeviceResponse response = client.sendDeviceRequest(
          new ClientDeviceRequest(androidFilter, "get_time", timeReq.toByteArray()));
      if (response.getStatus() == Status.SUCCESS) {
        String time = TimeResponse.parseFrom(response.getResponseData()).getTime();
        logger.info(
            String.format(
                "Response from device %s: %s %s",
                androidFilter.getDeviceId(), response.getStatus(), time));
      } else {
        fail("Failed to get time: " + response.getErrorMessage());
      }
    } catch (InvalidProtocolBufferException e) {
      fail("Failed to parse protocol buffer: " + e.getMessage());
    }
  }

  @Test
  public void testGetTimeOnAndroidDeviceAsync()
      throws InvalidProtocolBufferException, InterruptedException, ExecutionException,
          TimeoutException {
    TimeRequest timeReq = TimeRequest.newBuilder().setFormat("dd-MMM-yyyy hh:mm:ss").build();
    // Specify which device the request is sent to
    ClientDeviceFilter androidFilter =
        ClientDeviceFilter.newBuilder(DeviceType.ANDROID)
            .setDeviceId(ANDROID_DEVICE_SERIAL_NUM)
            .build();

    Future<ClientDeviceResponse> future = client.sendDeviceRequestAsync(
        new ClientDeviceRequest(androidFilter, "get_time", timeReq.toByteArray()));

    ClientDeviceResponse response = future.get(10, TimeUnit.SECONDS);
    assertNotNull(response);
    if (response.getStatus() == Status.SUCCESS) {
      String time = TimeResponse.parseFrom(response.getResponseData()).getTime();
      logger.info(
          String.format(
              "Response from device %s: %s %s",
              androidFilter.getDeviceId(), response.getStatus(), time));
    } else {
      fail("Failed to get time: " + response.getErrorMessage());
    }
  }
}
