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

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.Pair;
import android.util.Log;
import com.google.devicehub.proto.Device;
import com.google.devicehub.proto.DeviceHubGrpc;
import com.google.devicehub.proto.DeviceTypeEnum;
import com.google.devicehub.proto.SmartDeviceMessage;
import com.google.devicehub.proto.SmartDeviceMessageTypeEnum;
import com.google.devicehub.proto.SmartDeviceRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Android device client that communicate with the devicehub server to register itself and accept
 * requests to execute custom code
 */
public class AndroidDeviceClient {

  private static final String TAG = AndroidDeviceClient.class.getSimpleName();
  private static final int SHUTDOWN_WAIT_SECS = 5;

  private final ManagedChannel channel;
  private final DeviceHubGrpc.DeviceHubStub asyncStub;
  private final Map<String, Pair<CustomCodeHandler, Method>> customCodeHandlerMap;
  private final BlockingQueue<SmartDeviceRequest> requestQueue;
  private final Activity activity;
  private final ExecutorService requestMonitorExecutor;
  private final ExecutorService requestProcessorExecutor;

  public AndroidDeviceClient(Activity activity, String host, int port) {
    this.activity = activity;
    channel =
        ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext(true)
            .build();
    asyncStub = DeviceHubGrpc.newStub(channel);
    customCodeHandlerMap = new ConcurrentHashMap<>();
    requestQueue = new LinkedBlockingQueue<>(1);
    requestMonitorExecutor = Executors.newSingleThreadExecutor();
    requestProcessorExecutor = Executors.newCachedThreadPool();
  }

  public void shutdown() throws InterruptedException {
    Log.i(TAG, "Client shutdown");
    channel.shutdown().awaitTermination(SHUTDOWN_WAIT_SECS, TimeUnit.SECONDS);
    requestMonitorExecutor.shutdownNow();
    requestProcessorExecutor.shutdownNow();
  }

  /**
   * Register a custom code handler
   *
   * @param handler the custom hanler
   */
  public void registerHandler(CustomCodeHandler handler) {

    for (Method method : handler.getClass().getDeclaredMethods()) {
      Log.v(TAG, "Checking method: " + method.getName());
      if (method.isAnnotationPresent(CustomCodeTarget.class)) {
        Log.v(TAG, "Found custom code target method: " + method.getName());
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getReturnType().equals(byte[].class) && parameterTypes.length == 1) {
          Log.v(
              TAG,
              String.format(
                  "Registering method: %s/%s",
                  method.getName(), method.getAnnotation(CustomCodeTarget.class).name()));
          customCodeHandlerMap.put(
              method.getAnnotation(CustomCodeTarget.class).name(), Pair.create(handler, method));
        } else {
          Log.w(
              TAG,
              "Invalid signature for method: "
                  + method.getAnnotation(CustomCodeTarget.class).name());
        }
      }
    }
  }

  /**
   * Register the device on the hub by establish a bi-directional stream connection with the hub.
   *
   * @param id the Android device id
   */
  public void registerDevice(String id) {

    StreamObserver<SmartDeviceMessage> requestObserver =
        asyncStub.registerDevice(
            new StreamObserver<SmartDeviceRequest>() {
              // Gets triggered when the hub sends a request
              @Override
              public void onNext(SmartDeviceRequest request) {
                Log.i(TAG, "Received request: " + request.getEndpoint().getTargetName());
                try {
                  // Just enqueue the device request for the AndroidRequestProcessor to handle
                  requestQueue.put(request);
                } catch (InterruptedException e) {
                  Log.e(TAG, "Failed to enqueue the device request", e);
                  throw new RuntimeException(e);
                }
              }

              // Gets triggered when error occurs to the connection
              @Override
              public void onError(Throwable t) {
                Log.e(TAG, "Error occur during the communication with the hub: ", t);
                // Kill the activity
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Log.e(TAG, "Kill the activity");
                        activity.finish();
                      }
                    });
              }

              @Override
              public void onCompleted() {
                Log.i(TAG, "The communication with hub finished");
              }
            });

    Device.Builder deviceBuilder = Device.newBuilder();
    deviceBuilder.setType(DeviceTypeEnum.ANDROID);
    deviceBuilder.setId(id);
    SmartDeviceMessage.Builder smartDeviceMessageBuilder = SmartDeviceMessage.newBuilder();
    smartDeviceMessageBuilder.setMessageType(SmartDeviceMessageTypeEnum.REGISTER);
    smartDeviceMessageBuilder.setDevice(deviceBuilder.build());
    SmartDeviceMessage smartDeviceMessage = smartDeviceMessageBuilder.build();

    // Send the device registration request to the hub to establish the connection
    Log.i(TAG, "Sending device register request: " + smartDeviceMessage.getMessageType());
    requestObserver.onNext(smartDeviceMessage);

    // Start the request queue processor
    try {
      requestMonitorExecutor.execute(
          new AndroidRequestProcessor(
              requestObserver, customCodeHandlerMap, requestQueue, requestProcessorExecutor));
    } catch (Exception e) {
      Log.w(TAG, "Failed to launch the AndroidRequestProcessor", e);
      requestObserver.onError(e);
    }
  }
}
