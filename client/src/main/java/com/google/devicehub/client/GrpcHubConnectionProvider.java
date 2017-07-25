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

import com.google.devicehub.proto.DeviceHubGrpc;
import com.google.devicehub.proto.DeviceRequest;
import com.google.devicehub.proto.DeviceResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/** Provide GRPC based implementation of HubConnectionProvider */
public class GrpcHubConnectionProvider implements HubConnectionProvider {
  @Override
  public ManagedChannel getManagedChannel(String host, int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        // Channels are secure by default (via SSL/TLS), disable TLS to avoid needing
        // certificates.
        .usePlaintext(true)
        .build();
  }

  @Override
  public DeviceResponse sendDeviceRequest(ManagedChannel channel, DeviceRequest request) {
    return DeviceHubGrpc.newBlockingStub(channel).sendDeviceRequest(request);
  }
}
