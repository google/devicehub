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

package com.google.devicehub.device.examples;

import com.google.devicehub.device.CustomCodeHandler;
import com.google.devicehub.device.CustomCodeTarget;
import com.google.devicehub.device.examples.proto.TimeRequest;
import com.google.devicehub.device.examples.proto.TimeResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** A sample customer code handler class that implements a simple getTime() method */
public class TimeHandler extends CustomCodeHandler {

  @CustomCodeTarget(name = "get_time")
  public byte[] getTime(byte[] data) throws InvalidProtocolBufferException {

    TimeRequest request = TimeRequest.parseFrom(data);
    String time = getTime(request.getFormat());

    TimeResponse.Builder timeResponseBuilder = TimeResponse.newBuilder();
    timeResponseBuilder.setTime(time);

    return timeResponseBuilder.build().toByteArray();
  }

  private static String getTime(String format) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(format);
    return dateFormat.format(new Date());
  }
}
