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

import com.google.common.base.Strings;
import com.google.devicehub.proto.CustomCodeEndpoint;
import com.google.devicehub.proto.DeviceRequestStatusEnum;
import com.google.devicehub.proto.DeviceResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/** Class to monitor the device request queue for non-Android devices */
public class RegularRequestMonitor extends DeviceRequestMonitor {

  private static final Logger logger = HubLogger.getLogger(RegularRequestMonitor.class);
  private final DeviceRequestProcessor processor;

  public RegularRequestMonitor(
      DeviceRequestProcessor processor, BlockingQueue<DeviceRequestInfo> requestQueue) {
    super(requestQueue);
    this.processor = processor;
  }

  @Override
  protected void processRequest(DeviceRequestInfo deviceRequestInfo) {
    logger.info("Processing custom code endpoint: " + deviceRequestInfo);

    String errorMsg = null;
    boolean foundMethod = false;
    DeviceResponse response = null;
    CustomCodeEndpoint endpoint = deviceRequestInfo.getEndpoint();

    try {
      for (Method method : processor.getClass().getDeclaredMethods()) {
        logger.fine("Trying to match method: " + method.getName());
        if (!method.isAnnotationPresent(CustomCodeTarget.class)
            || !method
                .getAnnotation(CustomCodeTarget.class)
                .name()
                .equals(endpoint.getTargetName())) {
          continue;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getReturnType().equals(DeviceResponse.class) && parameterTypes.length == 1) {
          response =
              (DeviceResponse)
                  method.invoke(processor, endpoint.getCustomCodeParms().toByteArray());
          foundMethod = true;
          break;
        }
      }

      if (!foundMethod) {
        errorMsg = "Custom code target is not found: " + endpoint.getTargetName();
      }
    } catch (IllegalAccessException e) {
      errorMsg =
          String.format(
              "Illegal access the custom code target: %s : %s",
              endpoint.getTargetName(), e.getMessage());
    } catch (InvocationTargetException e) {
      errorMsg =
          String.format(
              "Failed to invoke the custom code target: %s : %s", endpoint.getTargetName(),
              e.getTargetException().getMessage());
    } catch (IllegalArgumentException e) {
      errorMsg =
          String.format(
              "Illegal argument provided to the custom code target: %s : %s",
              endpoint.getTargetName(), e.getMessage());
    } catch (Throwable t) {
      errorMsg =
          String.format(
              "Unknown problem happened when calling the custom code target: %s : %s",
              endpoint.getTargetName(), t.getMessage());
    }

    if (!Strings.isNullOrEmpty(errorMsg)) {
      logger.warning(errorMsg);

      // Create the response indicating something went wrong
      DeviceResponse.Builder builder =
          DeviceResponse.newBuilder().setStatus(DeviceRequestStatusEnum.FAILURE);
      builder.setErrorMessage(errorMsg);
      response = builder.build();
    }
    try {
      deviceRequestInfo.getResponseQueue().put(response);
    } catch (InterruptedException e) {
      logger.warning("Failed to enqueue response: " + e.getMessage());
    }
  }
}
