# DeviceHub

_This is not an official Google product._

`DeviceHub` is a multi-device test framework which enables you to write tests
that interact with multiple heterogeneous devices. The tests can run on a 
server as JUnit tests or on an Android device as instrumentation or UI 
Automator tests. 

The DeviceHub is based on a client/server architecture. In the middle of the
DeviceHub test framework is the DevieHub server which facilitates the 
communications among devices. The tests use device clients to communicate with the
DeviceHub server and send requests to other devices to perform certain actions.

For Android devices, you can add custom code to provide test specific functions by extending
CustomCodeHandler provided in the device directory, these functions can be called as
part of the test logic. For example, imagine a test scenario where two devices are involved: a
broadcaster and a receiver. The test is to verify that the message broadcasted by the former is
received on the latter correctly. Here are the test steps:

 1. The test asks the receiver to start accept new messages.
 2. The test asks the broadcaster to broadcast a message.
 3. The test asks the receiver if it has received the expected message.

 In this scenario, you can provide two custom code to receive message and to send message. The test
 logic will be executed on the server where two devices are connected and triggers the custom code
 accordingly and verify the result.

## Basic Usage

### To build and launch the DeviceHub server
    $ cd hub
    $ ./gradlew installDist
    $ DEVICEHUB_SERVER_OPTS=-Dconfig=sample.json build/install/devicehub-hub/bin/devicehub-server
    This should launch the hub server which listens on a port. The sample.json file specify the
    devices needed in the test environment.

### To build and install the device client example on a connected Android device
    $ adb reverse tcp:50008 tcp:50008
    $ cd device
    $ ./gradlew installDebug
    Then click on the app icon (DeviceHub Android Client) on the device. The device client should
    then connect the hub server and register itself. If you have multiple devices you can repeat
    the steps one by one. To provide other functions on the device, follow the TimeHanlder.java
    example in the device/examples directory.

### To run the example test
    $ cd examples
    $ ./gradlew test

## License

DeviceHub is licensed under the open-source [Apache 2.0 license](LICENSE).

## Contributing

Please [see the guidelines for contributing](CONTRIBUTING.md) before creating
pull requests.
