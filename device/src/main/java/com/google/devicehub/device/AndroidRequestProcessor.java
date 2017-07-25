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

package com.google.devicehub.device;

import android.support.v4.util.Pair;
import android.util.Log;
import com.google.devicehub.proto.CustomCodeEndpoint;
import com.google.devicehub.proto.DeviceRequestStatusEnum;
import com.google.devicehub.proto.DeviceResponse;
import com.google.devicehub.proto.SmartDeviceMessage;
import com.google.devicehub.proto.SmartDeviceMessageTypeEnum;
import com.google.devicehub.proto.SmartDeviceRequest;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/** The class to process the request from devicehub and return the response */
public class AndroidRequestProcessor implements Runnable {

  private static final String TAG = AndroidRequestProcessor.class.getSimpleName();

  private final StreamObserver<SmartDeviceMessage> requestObserver;
  private final Map<String, Pair<CustomCodeHandler, Method>> customCodeHandlerMap;
  private final BlockingQueue<SmartDeviceRequest> requestQueue;
  private final ExecutorService requestProcessorExecutor;

  public AndroidRequestProcessor(
      StreamObserver<SmartDeviceMessage> requestObserver,
      Map<String, Pair<CustomCodeHandler, Method>> customCodeHandlerMap,
      BlockingQueue<SmartDeviceRequest> requestQueue,
      ExecutorService requestProcessorExecutor) {
    this.requestObserver = requestObserver;
    this.customCodeHandlerMap = customCodeHandlerMap;
    this.requestQueue = requestQueue;
    this.requestProcessorExecutor = requestProcessorExecutor;
  }

  @Override
  public void run() {
    try {
      // Loop to process requests the thread is killed
      while (true) {
        final SmartDeviceRequest curRequest = requestQueue.take();

        requestProcessorExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                Log.i(TAG, "Processing request: " + curRequest.getEndpoint().getTargetName());

                DeviceResponse deviceResponse = invoke(curRequest.getEndpoint());

                SmartDeviceMessage.Builder smartDeviceMessageBuilder = SmartDeviceMessage.newBuilder();
                smartDeviceMessageBuilder.setMessageType(SmartDeviceMessageTypeEnum.CUSTOM_CODE_RESPONSE);
                smartDeviceMessageBuilder.setDeviceResponse(deviceResponse);
                smartDeviceMessageBuilder.setUuid(curRequest.getUuid());
                SmartDeviceMessage smartDeviceMessage = smartDeviceMessageBuilder.build();

                Log.i(TAG, "Sending device info: " + smartDeviceMessage.getMessageType());
                requestObserver.onNext(smartDeviceMessage);
              }
            });
      }
    } catch (InterruptedException e) {
      Log.i(TAG, "Failed to run custom code: ", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Invokes a registered custom code target method
   *
   * @param request the custom code request
   * @return the response
   */
  private DeviceResponse invoke(CustomCodeEndpoint request) {

    Pair<CustomCodeHandler, Method> customCode = customCodeHandlerMap.get(request.getTargetName());

    DeviceResponse.Builder deviceResponseBuilder = DeviceResponse.newBuilder();
    deviceResponseBuilder.setStatus(DeviceRequestStatusEnum.SUCCESS);

    String errMessage;
    if (customCode == null) {
      errMessage = "Couldn't find custom code target method: " + request.getTargetName();
      Log.w(TAG, errMessage);
      deviceResponseBuilder.setStatus(DeviceRequestStatusEnum.FAILURE);
      deviceResponseBuilder.setErrorMessage(errMessage);
      return deviceResponseBuilder.build();
    }

    Method method = customCode.second;
    try {
      byte[] result = (byte[]) method.invoke(customCode.first, request.getCustomCodeParms().toByteArray());
      if (result != null) {
        deviceResponseBuilder.setResponseData(ByteString.copyFrom(result));
      }
      return deviceResponseBuilder.build();
    } catch (IllegalAccessException e) {
      errMessage =
          String.format(
              "Illegal access the target method: %s: %s", request.getTargetName(), e.getMessage());
    } catch (InvocationTargetException e) {
      errMessage =
          String.format(
              "Failed to invoke the target method: %s: %s",
              request.getTargetName(), e.getTargetException());
    } catch (IllegalArgumentException e) {
      errMessage =
          String.format(
              "Failed to invoke the target method: %s",
              request.getTargetName(), e.getMessage());    }
    Log.w(TAG, errMessage);
    deviceResponseBuilder.setStatus(DeviceRequestStatusEnum.FAILURE);
    deviceResponseBuilder.setErrorMessage(errMessage);
    return deviceResponseBuilder.build();
  }
}
