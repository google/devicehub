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
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import dalvik.system.DexFile;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;

/** Simple activity class to start the devicehub android client */
public class AndroidClientActivity extends Activity {
  private static final String TAG = AndroidClientActivity.class.getSimpleName();
  private static final String HOST = "localhost";
  private static final int DEFAULT_PORT = 50008;
  private static final String HOST_PORT_KEY = "host_port";

  private AndroidDeviceClient client;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle bundle = getIntent().getExtras();
    int port = DEFAULT_PORT;
    if (bundle != null && bundle.containsKey(HOST_PORT_KEY)) {
      port = Integer.parseInt(bundle.getString(HOST_PORT_KEY));
    }

    client = new AndroidDeviceClient(this, HOST, port);

    // register all the handlers
    registerHandlers(client);

    // Register the device on the hub, only device serial is provided for now, should include other
    // dimensions in the future
    String deviceId = Build.SERIAL.toUpperCase();
    Log.i(TAG, "Registering device: " + deviceId);
    client.registerDevice(deviceId);
  }

  @Override
  public void onDestroy() {
    try {
      Log.i(TAG, "Shut down the device client");
      client.shutdown();
    } catch (InterruptedException e) {
      Log.e(TAG, "Failed to shut down the device client: ", e);
    } finally {
      super.onDestroy();
    }
  }

  /**
   * Register all the custom code handlers classes to the AndroidDeviceClient client so that they
   * can receive custom code requests
   *
   * @param client the AndroidDeviceClient
   */
  private void registerHandlers(AndroidDeviceClient client) {

    String packageName = this.getPackageName();
    Log.v(TAG, "package name: " + packageName);
    try {
      String apkName = this.getPackageManager().getApplicationInfo(packageName, 0).sourceDir;
      Log.v(TAG, "app name: " + apkName);

      DexFile dexFile = new DexFile(apkName);

      Enumeration<String> entries = dexFile.entries();
      while (entries.hasMoreElements()) {

        String entry = entries.nextElement();

        if (!entry.startsWith(packageName)) {
          continue;
        }
        Log.i(TAG, "Entry: " + entry);
        Class<?> clazz = Class.forName(entry);
        if (Modifier.isAbstract(clazz.getModifiers())) {
          Log.i(TAG, "Skip abstract class");
          continue;
        }

        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
          if (annotation.annotationType() != CustomCode.class) {
            continue;
          }
          Log.i(TAG, "Registering custom code handler: " + clazz.getName());

          Constructor<?> constructor = clazz.getConstructor();
          constructor.setAccessible(true);
          CustomCodeHandler handler = (CustomCodeHandler) constructor.newInstance();
          handler.setActivity(this);
          client.registerHandler(handler);
        }
      }
    } catch (IOException
        | NameNotFoundException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException
        | IllegalAccessException
        | InstantiationException e) {
      String errorMsg = "Failed to register custom code handlers: ";
      Log.e(TAG, errorMsg, e);
      throw new RuntimeException(errorMsg, e);
    }
  }
}
