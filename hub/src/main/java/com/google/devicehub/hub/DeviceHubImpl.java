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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devicehub.proto.Device;
import com.google.devicehub.proto.DeviceFilter;
import com.google.devicehub.proto.DeviceHubGrpc;
import com.google.devicehub.proto.DeviceRequest;
import com.google.devicehub.proto.DeviceRequestStatusEnum;
import com.google.devicehub.proto.DeviceResponse;
import com.google.devicehub.proto.DeviceTypeEnum;
import com.google.devicehub.proto.SmartDeviceMessage;
import com.google.devicehub.proto.SmartDeviceRequest;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/** Class to implement the methods defined in the proto file */
public class DeviceHubImpl extends DeviceHubGrpc.DeviceHubImplBase {

  private static final Logger logger = HubLogger.getLogger(DeviceHubImpl.class);

  private final Map<DeviceTypeEnum, List<DeviceRequestQueue>> requestQueues;
  private final List<DeviceRequestMonitor> requestMonitors;
  private final ContextProvider contextProvider;
  private final DeviceRequestProcessorFactory deviceRequestProcessorFactory;

  public DeviceHubImpl() {
    this(
        new ArrayList<>(),
        FileBasedContextProvider.getInstance(),
        new HubDeviceRequestProcessorFactory());
  }

  @VisibleForTesting
  DeviceHubImpl(
      List<DeviceRequestMonitor> requestMonitors,
      ContextProvider contextProvider,
      DeviceRequestProcessorFactory deviceRequestProcessorFactory) {
    super();

    this.requestQueues = new ConcurrentHashMap<>();
    this.requestMonitors = requestMonitors;
    this.contextProvider = contextProvider;
    this.deviceRequestProcessorFactory = deviceRequestProcessorFactory;

    initDeviceQueues();
  }

  @Override
  public void sendDeviceRequest(
      DeviceRequest request, StreamObserver<DeviceResponse> responseObserver) {
    try {
      logger.info(
          "Device request received, finding device request queue: " + request.getDeviceFilter());
      BlockingQueue<DeviceRequestInfo> requestQueue = findRequestQueue(request.getDeviceFilter());
      if (requestQueue == null) {
        String errorMsg =
            "Failed to find the device request queue for: " + request.getDeviceFilter();
        logger.warning(errorMsg);
        DeviceResponse response = DeviceResponse
            .newBuilder()
            .setStatus(DeviceRequestStatusEnum.FAILURE)
            .setErrorMessage(errorMsg)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        return;
      }

      BlockingQueue<DeviceResponse> responseQueue = new LinkedBlockingQueue<>();

      logger.info("Found device request queue, enqueueing the request");
      requestQueue.put(new DeviceRequestInfo(request.getCustomCodeEndpoint(), responseQueue));

      logger.info("Request enqueued, waiting for response");
      DeviceResponse response = responseQueue.take();
      logger.info(String.format(
          "Received response: %s %s", response.getStatus(), response.getErrorMessage()));

      responseObserver.onNext(response);
      responseObserver.onCompleted();
      logger.info("Sent response back to the client");

    } catch (InterruptedException e) {
      logger.warning("Failed to process send device request: " + e.getMessage());
    }
  }

  @Override
  public StreamObserver<SmartDeviceMessage> registerDevice(
      StreamObserver<SmartDeviceRequest> responseObserver) {

    // A new stream observer for each connected device
    return new StreamObserver<SmartDeviceMessage>() {
      private AndroidRequestMonitor androidRequestMonitor;
      private DeviceRequestQueue requestQueueMap;
      private ConcurrentHashMap<String, BlockingQueue<DeviceResponse>> responseQueueMap;

      @Override
      public void onNext(SmartDeviceMessage smartDeviceMessage) {
        logger.info("Received response from device: " + smartDeviceMessage);

        switch (smartDeviceMessage.getMessageType()) {
          case CUSTOM_CODE_RESPONSE:
            // Normal response from executing custom code
            logger.info("Enqueueing the response");
            try {
              BlockingQueue<DeviceResponse> responseQueue =
                  responseQueueMap.get(smartDeviceMessage.getUuid());
              if (responseQueue == null) {
                logger.warning(
                    "No response queue found for the custom code response: " + smartDeviceMessage);
              } else {
                responseQueue.put(smartDeviceMessage.getDeviceResponse());
              }
            } catch (InterruptedException e) {
              logger.warning("Failed to process device response: " + e.getMessage());
            }
            break;

          case REGISTER:
            // Register device response (request)
            Device device = smartDeviceMessage.getDevice();
            logger.info("Device registration, finding the device request queue: " + device);

            requestQueueMap = findDeviceRequestQueue(device);
            if (requestQueueMap == null) {
              // The device is not in the configuration file
              logger.warning(
                  "Registration rejected due to the device not in the configuration file: "
                      + device.getId());
              responseObserver.onCompleted();

              return;
            }

            // Create the processor thread which will monitor the request queue and send the
            // request to the device via responseObserver
            logger.info("Forking a request processor for the device");
            responseQueueMap = new ConcurrentHashMap<>();
            androidRequestMonitor =
                new AndroidRequestMonitor(
                    requestQueueMap.getQueue(), responseObserver, responseQueueMap);
            List<DeviceRequestMonitor> processors = Collections.synchronizedList(requestMonitors);
            processors.add(androidRequestMonitor);
            new Thread(androidRequestMonitor).start();
            break;

          default:
            throw new RuntimeException(
                "Invalid smart device info type: " + smartDeviceMessage.getMessageType());
        }
      }

      @Override
      public void onError(Throwable t) {
        logger.info("onError received");
        if (androidRequestMonitor != null) {
          androidRequestMonitor.finished();
        }
        requestQueueMap.setQueue(null);
      }

      @Override
      public void onCompleted() {
        logger.info("onCompleted received");
        if (androidRequestMonitor != null) {
          androidRequestMonitor.finished();
        }
        responseObserver.onCompleted();
        requestQueueMap.setQueue(null);
      }
    };
  }

  public void shutdown() {
    // Close all the request processor threads
    List<DeviceRequestMonitor> processors = Collections.synchronizedList(requestMonitors);
    synchronized (processors) {
      for (DeviceRequestMonitor processor : processors) {
        processor.finished();
      }
    }
  }

  /** Initialize the device request queue with the devices listed in the config file, */
  private void initDeviceQueues() {
    Map<DeviceTypeEnum, ImmutableList<Device>> deviceMap = contextProvider.getDeviceMap();
    if (deviceMap.isEmpty()) {
      logger.warning("No devices to initialize");
      return;
    }

    for (Map.Entry<DeviceTypeEnum, ImmutableList<Device>> curMap : deviceMap.entrySet()) {
      List<DeviceRequestQueue> deviceRequestQueues = new ArrayList<DeviceRequestQueue>();
      for (Device device : curMap.getValue()) {
        logger.info("Parsed device and adding to request queue: " + device);

        BlockingQueue<DeviceRequestInfo> deviceRequestInfoQueue = null;
        // Android device is handled differently in registerDevice()
        if (curMap.getKey() != DeviceTypeEnum.ANDROID) {
          deviceRequestInfoQueue = new LinkedBlockingQueue<>();
          DeviceRequestProcessor processor = deviceRequestProcessorFactory.create(device);
          RegularRequestMonitor monitor =
              new RegularRequestMonitor(processor, deviceRequestInfoQueue);
          List<DeviceRequestMonitor> processors = Collections.synchronizedList(requestMonitors);
          processors.add(monitor);
          new Thread(monitor).start();
        }

        DeviceRequestQueue deviceRequestQueue =
            new DeviceRequestQueue(device, deviceRequestInfoQueue);

        deviceRequestQueues.add(deviceRequestQueue);
      }
      requestQueues.put(curMap.getKey(), deviceRequestQueues);
    }
  }

  /**
   * Finds the device request queue for a given device. This is called when a device registers. The
   * logic is to go through the devices that are listed in the config file and try to match the the
   * id first, if that failed it will find the first one with an empty id and empty queue (means no
   * device has registered)
   *
   * @param device the device to be registered
   * @return the device request queue if found, null otherwise
   */
  private DeviceRequestQueue findDeviceRequestQueue(Device device) {
    if (!requestQueues.containsKey(device.getType())) {
      return null;
    }

    List<DeviceRequestQueue> requestQueue =
        Collections.synchronizedList(requestQueues.get(device.getType()));
    DeviceRequestQueue queueFound = null;

    synchronized (requestQueue) {
      if (requestQueue != null && !requestQueue.isEmpty()) {
        // First loop over trying to match the id
        for (DeviceRequestQueue curQueue : requestQueue) {
          // For now only ID
          String id = curQueue.getDevice().getId();
          if (id != null && id.equalsIgnoreCase(device.getId())) {
            queueFound = curQueue;
            break;
          }
        }
        // Now find the first one with an empty id which means the device id is not specified in
        // the json config file, so any android device is a match and can register as the specified
        // device. This is to support cases where the test doesn't care which specific device
        // to use, it just need talk to an android device, so no other dimensions need be provided
        // in the json config file.
        if (queueFound == null) {
          for (DeviceRequestQueue curQueue : requestQueue) {
            String id = curQueue.getDevice().getId();
            if ((id == null || id.isEmpty()) && curQueue.getQueue() == null) {
              queueFound = curQueue;
              break;
            }
          }
        }

        if (queueFound != null && queueFound.getQueue() == null) {
          // When the devicehub gets initialized it will add entries for the devices listed in the
          // config file, when device registers we will add the request queue so that further
          // device requests can be queued here
          queueFound.setQueue(new LinkedBlockingQueue<>());
        }
      }
    }

    return queueFound;
  }

  /**
   * Finds the device request queue for a given device filter. This is called when a test client
   * sends a device request. The logic is to go through the devices request queues and match the
   * device id first if one is present in the device filter, otherwise match the label.
   *
   * @param deviceFilter the device to be registered
   * @return the request queue if found, null otherwise
   */
  private BlockingQueue<DeviceRequestInfo> findRequestQueue(DeviceFilter deviceFilter) {
    if (!requestQueues.containsKey(deviceFilter.getDeviceType())) {
      return null;
    }

    List<DeviceRequestQueue> requestQueue =
        Collections.synchronizedList(requestQueues.get(deviceFilter.getDeviceType()));
    synchronized (requestQueue) {
      if (requestQueue != null && !requestQueue.isEmpty()) {
        String id = deviceFilter.getDeviceId();
        String label = deviceFilter.getDeviceLabel();
        logger.info("Current filter: " + deviceFilter);

        for (DeviceRequestQueue curQueue : requestQueue) {
          Device device = curQueue.getDevice();
          // Match ID or label
          if ((!Strings.isNullOrEmpty(id) && id.equalsIgnoreCase(device.getId()))
              || (!Strings.isNullOrEmpty(label) && label.equals(device.getLabel()))) {
            logger.info(String.format("Found queue: %s/%s", id, label));
            return curQueue.getQueue();
          }
        }
      }
    }
    return null;
  }
}
