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

import static com.google.common.base.Charsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devicehub.proto.Device;
import com.google.devicehub.proto.DeviceTypeEnum;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A singleton to provide the information of the environment in which the device hub runs */
public class FileBasedContextProvider implements ContextProvider {
  private static final Logger logger = HubLogger.getLogger(FileBasedContextProvider.class);

  private static final String CONFIG_ARGUMENT = "config";
  private static final String JSON_CONFIG_CONTROLLERS_KEY = "Controllers";
  private static final String JSON_DEVICE_SERIAL_KEY = "serial";
  private static final String JSON_DEVICE_LABEL_KEY = "label";
  private static final String JSON_DEVICE_DIMENSIONS_KEY = "dimensions";
  private static final String JSON_DEVICE_DIMENSIONS_KEY_KEY = "key";
  private static final String JSON_DEVICE_DIMENSIONS_VALUE_KEY = "value";

  private static final ImmutableMap<DeviceTypeEnum, String> DEVICE_TYPE_KEY_MAP;

  static {
    ImmutableMap.Builder<DeviceTypeEnum, String> builder = ImmutableMap.builder();
    builder.put(DeviceTypeEnum.ANDROID, "AndroidDevice");
    builder.put(DeviceTypeEnum.SPIRENT_RPS, "SpirentRPS");
    builder.put(DeviceTypeEnum.WEB_CAM, "WebCam");
    builder.put(DeviceTypeEnum.GENERIC_SCRIPT, "GenericScript");
    builder.put(DeviceTypeEnum.ROBOTIC_DEVICE, "RoboticDevice");
    builder.put(DeviceTypeEnum.CHAMELEON_DEVICE, "ChameleonDevice");

    DEVICE_TYPE_KEY_MAP = builder.build();
  }

  private static FileBasedContextProvider instance = null;
  private ImmutableMap<DeviceTypeEnum, ImmutableList<Device>> deviceMap;

  private FileBasedContextProvider() {
    init();
  }

  public static FileBasedContextProvider getInstance() {

    if (instance == null) {
      instance = new FileBasedContextProvider();
    }

    return instance;
  }

  @Override
  public Map<DeviceTypeEnum, ImmutableList<Device>> getDeviceMap() {
    return deviceMap;
  }

  /**
   * Loads the test config file and populate the test environment. The Mobile Harness test driver
   * will trigger the devicehub server using -Dconfig=<Config_File>. The config file will be in json
   * format
   */
  private void init() {

    String configFileName =
        Preconditions.checkNotNull(System.getProperty(CONFIG_ARGUMENT), "No config file provided");

    try {
      String configString = readFile(configFileName);
      JSONArray config = new JSONArray(configString);
      // Only support one testbed per config file for now
      JSONObject testBed = config.getJSONObject(0);

      if (testBed == null) {
        logger.warning("No testbed found in the test config file: " + configFileName);
        return;
      }
      if (testBed.has(JSON_CONFIG_CONTROLLERS_KEY)) {
        testBed = testBed.getJSONObject(JSON_CONFIG_CONTROLLERS_KEY);
      }

      ImmutableMap.Builder<DeviceTypeEnum, ImmutableList<Device>> mapBuilder =
          ImmutableMap.builder();
      for (Map.Entry<DeviceTypeEnum, String> entry : DEVICE_TYPE_KEY_MAP.entrySet()) {

        JSONArray curDevices = testBed.optJSONArray(entry.getValue());
        if (curDevices == null) {
          continue;
        }

        logger.info("Parsing device type: " + entry.getKey());
        ImmutableList.Builder<Device> listBuilder = ImmutableList.builder();
        for (int ind = 0; ind < curDevices.length(); ++ind) {
          JSONObject curJsonDevice = curDevices.getJSONObject(ind);
          // The id and label fields could be empty
          String id = curJsonDevice.optString(JSON_DEVICE_SERIAL_KEY);
          String label = curJsonDevice.optString(JSON_DEVICE_LABEL_KEY);

          Device.Builder deviceBuilder = Device.newBuilder();
          deviceBuilder.setId(id).setLabel(label).setType(entry.getKey());

          JSONArray dimensions = curJsonDevice.optJSONArray(JSON_DEVICE_DIMENSIONS_KEY);
          if (dimensions != null) {
            for (int di = 0; di < dimensions.length(); ++di) {
              JSONObject curDimension = dimensions.getJSONObject(di);
              deviceBuilder.putDimensions(
                  curDimension.getString(JSON_DEVICE_DIMENSIONS_KEY_KEY),
                  curDimension.getString(JSON_DEVICE_DIMENSIONS_VALUE_KEY));
            }
          }
          Device device = deviceBuilder.build();

          logger.info("Adding device: " + device);
          listBuilder.add(device);
        }

        mapBuilder.put(entry.getKey(), listBuilder.build());
      }

      deviceMap = mapBuilder.build();

    } catch (JSONException e) {
      throw new RuntimeException("Failed to parse testbed config " + configFileName, e);
    }
  }

  private static String readFile(String configFileName) {
    File configFile = new File(configFileName);
    if (!configFile.exists()) {
      throw new RuntimeException("Configuration file doesn't exist: " + configFileName);
    }

    try {
      byte[] encoded = Files.readAllBytes(Paths.get(configFileName));
      return new String(encoded, UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read configuration file: " + configFileName, e);
    }
  }
}
