# LoopKey Integration Sample Project

This project is a sample of how to integrate the LoopKey BLE communication technology with your Android project.

## LoopKey

LoopKey provides hardware and software libraries to empower Internet of Things applications. Use our technology
to reduce development cost and bring your product to market faster. This project exemplifies how easy it is to use
LoopKey to authenticate with real-world objects.

If you're interested in learning more about LoopKey, contact us at loopkey@loopkey.com.br.

## Getting Started

**You will find two samples under this directory, one with Jetpack Compose(placed at ComposableSample) and one with standard activity(placed under SampleLoopKey).**

To be able to scan to lockers and unlock them, some files are necessary.

1. Copy the entire folder **Composable Sample > app > libs to your Project > App destination**;
2. Copy the entire folder **Composable Sample > app > src > main > java > br > com > loopkey > sample lk, to your Project > App > src folder**;
3. Open the class copied to your project and fix the Package Strcture;
4. At your build.gradle placed at the root of the project(It's NOT the one placed under app folder), paste the content below or adapt the existing one to avoid problems with version mismatch of Kotlin plugin:

```groovy
buildscript {
    ext {
        kotlin_version = '1.7.10'
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id 'com.android.application' version '7.2.2' apply false
    id 'com.android.library' version '7.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.5.31' apply false
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

5. At build.gradle placed under the app directory add the following:
```groovy
dependencies {
    // LoopKey Mandatory Dependencies
    // Please, does not change this version of codec. All subsequent versions removes some important methods.
    implementation 'commons-codec:commons-codec:1.15'
    implementation 'com.google.code.gson:gson:2.10'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
    implementation "com.squareup.moshi:moshi-kotlin:1.13.0"
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation "com.squareup.retrofit2:converter-moshi:2.9.0"
    implementation 'com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.2'
    // Responsible to load the /libs directory under the classpath of the application.
    implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')
}
```

6. At your AndroidManifest.xml file, add the permissions:

``` xml
    <!-- Location Setup -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    
    <!-- BLE Setup -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
    <!-- Request legacy Bluetooth permissions on older devices. -->
    <uses-permission android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
```

## Controling the Lockers

1. In order to scan for devices, your class will need to implement the LKScannerProtocol, for example:

```kotlin
class Example: LKScannerProtocol
{
    var _reachableDevices: ReachableDevices = mutableListOf() // Reachable devices obtained via LoopKey SDK Scanner
    private lateinit var _loopkeyBLEScanner: LKReachableDevice
}
```

2. You MUST request the instance of the scanner after your applications is fully initialized to avoid any kind of mistake that may be caused by unavailable sources during the startup time;

```kotlin
class Example: LKScannerProtocol
{
    var _reachableDevices: ReachableDevices = mutableListOf() // Reachable devices obtained via LoopKey SDK Scanner
    private lateinit var _loopkeyBLEScanner: LKReachableDevice

    override fun onViewCreated()
    {
        _loopkeyBLEScanner = LKReachableDeviceImpl.INSTANCE
        _loopkeyBLEScanner.subscribe(this)
    }
}
```

3. Before requesting scanner to notify you about new lockers, be sure you have requested runtime to the user when necessary accordingly to Android Versions. The necessary permissions are different before and after Android S:

```
Before Android S
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, BLUETOOTH, BLUETOOTH_ADMIN
```

```
After Android S
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT
```

The permissions can be requested by using the snipper below and you can change it accordingly to your needs.

```kotlin
private fun _requireBluetoothAndLocationPermissionsIfNecessary() {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsAndResults ->
                unlockInteractor = LKUnlockDeviceInteractor(applicationContext)
                scanner = LKScanDevices(applicationContext)
                scanner.subscribe(this)
            }
        val necessaryPermissions = mutableListOf<String>()

        if (!_isPermissionToLocationCoarseGranted()) {
            necessaryPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!_isBluetoothPermissionGranted(this) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        ) {
            necessaryPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            necessaryPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (necessaryPermissions.size <= 0) {
            unlockInteractor = LKUnlockDeviceInteractor(applicationContext)
            scanner = LKScanDevices(applicationContext)
            scanner.subscribe(this)
        }

        requestPermissionLauncher.launch(necessaryPermissions.toTypedArray())
    }
```

4. Finally, you will need to implement the `didUpdateVisible(visibleDevices: List<LKScanResult>)` method, wich will run every time a new device is detected or gets out of reach. It receives an array of `LKScanResult` containing the visible devices as the scan time:

5. After you make sure your device is reachable, you may use commands. You may check if a door is in the reachable devices by requesting to the scanner a visible device using the serial or the lockMac:

```kotlin
    scanner.get(serialBase32, lockMac)
```

6. To send the Unlock Command to the locker you must instantiate the `LKUnlockDeviceInteractor` class.

```kotlin
class Example: LKScannerProtocol
{
    var _reachableDevices: ReachableDevices = mutableListOf() // Reachable devices obtained via LoopKey SDK Scanner
    private lateinit var _loopkeyBLEScanner: LKReachableDevice
    private lateinit var _unlockInteractor: LKUnlockDeviceInteractor

    override fun onViewCreated()
    {
        _requireBluetoothAndLocationPermissionsIfNecessary()
    }

    fun onPermissionsGranted()
    {
        this._unlockInteractor = LKUnlockDeviceInteractor(context = applicationContext)
        this._scanner = LKScanner(context = applicationContext)
        _scanner.subscribe(this)
    }
}
```

7. To send a Unlock command to any locker you must have the fields: `lockData`, `lockMac`, `serial`, `userKey`. Some of the fields could be null accordingly to the lock type. The field Serial may come as base64 and/or base32 encoding depending on how you fetch data from the API. **By default, the Scanner and Unlock classes uses the serial as Base32.**

8. To send a Unlock command to a locker you simple need to call the function on the UnlockInteractor Class.

```kotlin
_unlockInteractor.unlock(serialBase32 = serialBase32,
            lockMac = lockMac,
            userKey = userKey,
            lockData = lockData) { result ->
        }
``` 

If the door is not in range, you will receive and error informing about it on the result callback. All possible values of results can be found at LKDeviceUnlockInteractor.kt file.

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

Copyright (c) 2021 LoopKey

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
