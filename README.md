# LoopKey Integration Sample Project

This project is a sample of how to integrate the LoopKey BLE communication technology with your Android project.

## LoopKey

LoopKey provides hardware and software libraries to empower Internet of Things applications. Use our technology
to reduce development cost and bring your product to market faster. This project exemplifies how easy it is to use
LoopKey to authenticate with real-world objects.

If you're interested in learning more about LoopKey, contact us at loopkey@loopkey.com.br.

### Scanning for devices

In order to scan for devices, your class will need to implement the ReachableDevices.Listener, for example:

```
class Example: ReachableDevices.Listener
{
    var _reachableDevices: ReachableDevices
    var _doorModel: DoorModel
}
```

Then, you will need to subcribe for notifications as soon as possible in your lifecycle (`onCreate`, `onCreateView`, etc):

```
    _reachableDevices.subscribe(this)
```

On your `onDestroy`, unsubscribe for notifications:

```
    _reachableDevices.unsubscribe(this)
```

Finally, you will need to implement the `onReachableDevices()` method, wich will run every time a new device is detected or gets out of reach. It receives an array of `BluetoothModel` containing the visible devices as the scan time:

```
    override fun onReachableDevices(reachableDevices: Collection<BluetoothModel>) { }
```

Now you have an array of `BluetoothModel`, that you can use for your device retrieval logic.

After you make sure your device is reachable, you may use commands. You may check if a door is in the reachable devices by checking if the serial of the device is in the `BluetoothModel` array, for example:

```
    fun getBluetoothModelFromList(bluetoothModels: Collection<BluetoothModel>, serialCode: String): BluetoothModel?
    {
        val results = bluetoothModels.filter { Arrays.equals(it.serialCode, SignatureUtils.decodeBase64(serialCode)) }
        if (results.isNotEmpty()) {
            return results.first()
        }

        return null
    }
```

### Using commands

The next step is to set the admin and user keys to this device. You may set them directly on the device you've just retrieved.

```
    _doorModel.bluetoothModel = getBluetoothModelFromList(bluetoothModels, YOUR_SERIAL)
    _doorModel.adminKey = YOUR_ADMIN_KEY
    _doorModel.key = YOUR_USER_KEY
```

_Both the keys and the serial need to be `String`. They come as base64 encoded string from the server API._

With your device ready you'll need transform Door to a CommDevice:

```
    val commDevice = LKCommDevice(Base64.decode(_doorModel.serial, Base64.DEFAULT)
    commDevice.key = Base64.decode(_doorModel.key, Base64.DEFAULT)
    commDevice.adminKey = Base64.decode(_doorModel.adminKey, Base64.DEFAULT)
```

Then, get the communicator:

```
    val _bleManage: LKReachableDevice
    val comm = _bleManager.deviceCommunicatorForDevice(commDevice, userId);
```

Finally, create command and run then:

```
    val command = LKCommandRepository.createUnlockCommand(LKUnlockCommand.Listener)
    comm.execute(command)
```

On `LKUnlockCommand.Listener` you can get the response of LoopKey (`OKOK`, `OKAO`, etc).

That's it. Your command will be run by the command runner.

Note that the unlock command was used as an example, but other commands are similar. Just check the documentation on the LKCommandRepository for more information.

### Retrieving data from server

The first thing you'll want to do is login. The endpoint is:

```
/login
POST
Parameters:
- email
- pass
Response:
- authorization
- user
```

This will return the token used for authentication and usage of other endpoints.

You will also want to retrieve the doors list. Simply use the endpoint:

```
/permission
GET
Response:
- id
- whoShared
- door
- referenceType
- referenceValue
- type
- contact
```

Which will return a list of devices for that user. The door will contain both the serial and the keys for your devices.

You may check a more complete reference on the server API by checking the link: https://dev.loopkey.com.br/

## License

**The license is for this sample repository, not for the LoopKey BLE communication which is proprietary license.**

MIT License

Copyright (c) 2018 LoopKey

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions
of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.