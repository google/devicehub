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

import com.google.common.base.Strings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Logger class to log the message into a series of log files so that they can be uploaded to Sponge
 */
public class HubLogger {

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("YY/MM/dd HH:mm:ss:SSS");
  private static final String ENV_LOG_DIR = "TEST_UNDECLARED_OUTPUTS_DIR";
  private static final int MAX_LOG_FILE_SIZE = 10 * 1024 * 1024;
  private static final int MAX_LOG_FILE_COUNT = 100;

  private static FileHandler fileHandler;

  static class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      Date date = new Date(record.getMillis());
      String msg =
          String.format(
              "%s %s %s [%s] %s\n",
              DATE_FORMAT.format(date),
              record.getLevel().toString().substring(0, 1),
              record.getLoggerName(),
              record.getSourceMethodName(),
              record.getMessage());

      Throwable e = record.getThrown();
      if (e != null) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(stream));
        msg += new String(stream.toByteArray(), UTF_8);
      }
      return msg;
    }
  };

  public static Logger getLogger(Class<?> clazz) {

    Logger logger = Logger.getLogger(clazz.getName());
    final String logDir = System.getenv(ENV_LOG_DIR);
    if (Strings.isNullOrEmpty(logDir)) {
      return logger;
    }

    // FileHandler is thread safe so we share the same handler here in order for the message to
    // logged into the same file
    if (fileHandler == null) {
      try {
        fileHandler =
            new FileHandler(
                logDir + File.separator + "devicehub_server_log%g.txt",
                MAX_LOG_FILE_SIZE,
                MAX_LOG_FILE_COUNT);
        fileHandler.setFormatter(new LogFormatter());
      } catch (IOException e) {
        throw new RuntimeException("Failed to create file handler for logger", e);
      }
    }

    logger.addHandler(fileHandler);

    return logger;
  }
}
