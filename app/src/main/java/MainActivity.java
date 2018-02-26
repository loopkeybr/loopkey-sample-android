package br.com.loopkey.indigo.loopkeylib;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import br.com.loopkey.indigo.loopkeylib.commands.LKCommand;
import br.com.loopkey.indigo.loopkeylib.commands.LKDeviceCommunicator;
import br.com.loopkey.indigo.loopkeylib.commands.implementations.LKCustomCommand;
import br.com.loopkey.indigo.loopkeylib.core.LKReachableDevice;
import br.com.loopkey.indigo.loopkeylib.core.LKReachableDeviceImpl;
import br.com.loopkey.indigo.loopkeylib.core.LKReachableDeviceListener;
import br.com.loopkey.indigo.loopkeylib.entity.LKCommDevice;
import br.com.loopkey.indigo.loopkeylib.entity.LKEntity;
import br.com.loopkey.indigo.loopkeylib.message.LKMessageSource;

public class MainActivity extends AppCompatActivity implements LKReachableDeviceListener
{
    private LKReachableDevice _lkReachableDevice;

    private LKDeviceCommunicator _communicator;

    @Override
    public void onCreate(Bundle savedInstance)
    {
        super.onCreate(savedInstance);

        // LoopKey library initialization.
        LKLib.install(this.getApplicationContext(), new LKMainThreadImpl());

        // Get reference to device scanner.
        _lkReachableDevice = LKReachableDeviceImpl.newInstance();

        // Subscribe to notifications.
        _lkReachableDevice.subscribe(this);
    }

    @Override
    public void onDestroy()
    {
        // Unsubscribe to notifications.
        _lkReachableDevice.unsubscribe(this);

        super.onDestroy();
    }

    @Override
    public void onReachableDevices(List<LKEntity> lkEntityList)
    {
        // Here, we obtain iterate through all the currently reachable devices.
        for (LKEntity device : lkEntityList) {
            Log.d("LKLIB", device.getName());
            Log.d("LKLIB", Base64.encodeToString(device.getSerialCode(), 0));
            Log.d("LKLIB", "Device Roll Count (from broadcast): " +
                    Base64.encodeToString(device.getRollCount(), 0));

            if (Arrays.equals(device.getSerialCode(), Base64.decode("XXX", 0))) { // If this is the device I'm looking for, send message.
                LKCommDevice commDevice = new LKCommDevice(device.getSerialCode());
                commDevice.name = device.getName();
                _communicate(commDevice);
            }
        }
    }

    /**
     * Communicates with the given LoopKey device and the associated user ID.
     * @param commDevice The representation of the device that will be connected with. The object
     *                   must contain at least its serial number to establish a connection.
     */
    private void _communicate(LKCommDevice commDevice)
    {
        if (_communicator != null) {
            return; // In this example, we allow one connection at a time.
        }

        // The first step is to create a custom command object which will be responsible for
        // notifying the communication events with the LoopKey device.
        LKCustomCommand customCommand = new LKCustomCommand(new LKCustomCommand.Listener() {
            @Override
            public void mountMessage(LKCommDevice device, int userId,
                                     LKCustomCommand.LKCustomCommandMountedMessageCallback callback) {
                // This is called once a connection has been established with the LoopKey device.

                // Hence, it is possible to obtain the updated device's roll count using the
                // LKCommDevice object. The roll count is useful to create messages to the device,
                // thus it is usually passed to the backend.
                byte[] rollCount = device.rollCount;
                Log.d("LKLIB", "The connected device has roll count: " + _bytesToHex(rollCount));

                // This method should be used to create the message that will be sent to the
                // LoopKey device. When the message is ready, callback.onMounted() should be called.
                // In case of failure while creating the message, callback().onError() should be
                // called so the connection with the LoopKey is closed.

                // Here, we are mounting a message. However, in practice, this should be received from
                // the backend.
                final byte[] messageToSend = Base64.decode("AWE9ccFIpavL70OqbcB362zMzehwvHqKhfeDXj1hS4XRFyIZZ8Mf/1Neu93yg31fpQ==",0);

                // Finally, we send it to the LoopKey device.
                callback.onMounted(messageToSend);
            }

            @Override
            public void onResult(byte[] response, LKMessageSource responseSource) {
                // This method is called whenever we receive a raw response from the device.
                Log.d("LKLIB", "SUCCESS: " + _bytesToHex(response));
                _communicator = null;
            }

            @Override
            public void onError(LKCommand.Error error) {
                // This is called if we do not get a response from the device.
                Log.d("LKLIB", "ERROR: " + error);
                _communicator = null;
            }
        });

        // After a command object is created, we instantiate a communicator object with the
        // device that we will connect with the the user ID (should be 0 if not applicable) that is
        // executing such command.
        _communicator = _lkReachableDevice.deviceCommunicatorForDevice(commDevice, 0);

        // We check if the device is available for communication. If it isn't, we abort.
        if (_communicator == null) {
            Log.d("LKLIB", "ERROR: device is unavailable.");
            return;
        }

        // Finally, we pass the command object to the communicator so that the connection can
        // be established.
        _communicator.execute(customCommand);
    }

    /**
     * Utility method to format a byte array into a string containing its hexadecimal contents.
     * @param bytes The bytes to be converted.
     * @return The formatted string.
     */
    private static String _bytesToHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
