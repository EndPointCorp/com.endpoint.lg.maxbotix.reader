/*
 * Copyright (C) 2015 End Point Corporation
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.endpoint.lg.maxbotix.reader;

import com.endpoint.lg.support.proximity.ProximityEvent;
import com.endpoint.lg.support.message.ProximityMessages;

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import interactivespaces.service.comm.serial.SerialCommunicationEndpoint;
import interactivespaces.service.comm.serial.SerialCommunicationEndpointService;
import interactivespaces.util.concurrency.CancellableLoop;
import interactivespaces.util.data.json.JsonBuilder;
import interactivespaces.util.resource.ManagedResourceWithTask;

/**
 * Reads events from a Maxbotix proximity sensor.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class MaxbotixReaderActivity extends BaseRoutableRosActivity {
  /**
   * Configuration key for serial port location.
   */
  public static final String CONFIG_KEY_SERIAL_DEVICE_PATH = "lg.maxbotix.reader.device";

  /**
   * The length of a message from the sensor, in bytes.
   */
  public static final int MESSAGE_LENGTH = 8;

  /**
   * Serial baud rate for the sensor.
   */
  public static final int BAUD_RATE = 57600;

  private SerialCommunicationEndpointService serialService;
  private SerialCommunicationEndpoint serialPort;

  private void handleProximityFrame(String frame) {
    int distance = Integer.parseInt(frame.substring(1, 4));
    boolean presence = (frame.getBytes()[6] == 0x31);

    getLog().debug(String.format("distance: %d\tpresence: %s", distance, presence));

    if (isActivated()) {
      ProximityEvent proxEvent = new ProximityEvent(distance, presence);
      JsonBuilder message = new JsonBuilder();
      ProximityMessages.serializeProximityEvent(proxEvent, message);
      sendOutputJsonBuilder("proximity", message);
    }
  }

  private class SerialReader extends CancellableLoop {
    private final byte[] buffer = new byte[MESSAGE_LENGTH];

    @Override
    protected void loop() throws InterruptedException {
      if (serialPort.available() >= MESSAGE_LENGTH) {
        serialPort.read(buffer);

        String frame = new String(buffer);
        handleProximityFrame(frame);
      }
    }

    @Override
    protected void handleException(Exception e) {
      getLog().error("Exception while reading serial port", e);
    }
  }

  /**
   * Spins up the serial reader.
   */
  @Override
  public void onActivityStartup() {
    serialService =
        getSpaceEnvironment().getServiceRegistry().getService(
            SerialCommunicationEndpointService.SERVICE_NAME);

    String devicePath = getConfiguration().getRequiredPropertyString(CONFIG_KEY_SERIAL_DEVICE_PATH);
    serialPort = serialService.newSerialEndpoint(devicePath);
    serialPort.setBaud(BAUD_RATE);
    addManagedResource(new ManagedResourceWithTask(serialPort, new SerialReader(),
        getSpaceEnvironment()));
  }
}
