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

import com.google.devicehub.proto.Device;
import java.util.concurrent.BlockingQueue;

/** Represents a map between a device and its device request info queue */
public class DeviceRequestQueue {

  private final Device device;
  private BlockingQueue<DeviceRequestInfo> queue;

  public DeviceRequestQueue(Device device, BlockingQueue<DeviceRequestInfo> queue) {
    this.device = device;
    this.queue = queue;
  }

  public Device getDevice() {
    return device;
  }

  public BlockingQueue<DeviceRequestInfo> getQueue() {
    return queue;
  }

  public void setQueue(BlockingQueue<DeviceRequestInfo> queue) {
    this.queue = queue;
  }
}
