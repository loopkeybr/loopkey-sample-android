package br.com.loopkey.sample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.loopkey.indigo.lklib.LKLib
import br.com.loopkey.indigo.lklib.commands.LKCommand
import br.com.loopkey.indigo.lklib.commands.LKCommandRepository.createUnlockCommand
import br.com.loopkey.indigo.lklib.commands.LKCommandRunner
import br.com.loopkey.indigo.lklib.commands.implementations.LKUnlockCommand
import br.com.loopkey.indigo.lklib.core.LKReachableDevice
import br.com.loopkey.indigo.lklib.core.LKReachableDeviceImpl
import br.com.loopkey.indigo.lklib.core.LKReachableDeviceListener
import br.com.loopkey.indigo.lklib.entity.LKCommDevice
import br.com.loopkey.sample.databinding.ActivityMainBinding
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity(), LKReachableDeviceListener {
    private var _lkReachableDevice: LKReachableDevice? = null

    private var _isUnlocking = false

    private var _success: Int = 0

    private var _failure: Int = 0

    private lateinit var binding: ActivityMainBinding

    private var _hasTestInExecution: Boolean = false

    private var _serialBase64: String = "<DOOR SERIAL ENCODED IN BASE64>"

    private var _adminKeyBase64: String = "<ADMIN KEY ENCODED IN BASE64>"

    private var _key64: String = "<USER KEY ENCODED IN BASE64>="

    private var _startTimeInMs: Long = 0

    private var _commandStartExecution: Long = 0

    private var _timer: Timer = Timer()

    private var _executionTimes: MutableList<Long> = mutableListOf()

    public override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // LoopKey library initialization.
        LKLib.install(this.applicationContext)

        // Get reference to device scanner.
        _lkReachableDevice = LKReachableDeviceImpl.INSTANCE

        _requireBluetoothAndLocationPermissionsIfNecessary()

        binding.startStopButton.setOnClickListener {
            if (_hasTestInExecution) {
                _hasTestInExecution = false
                binding.startStopButton.text = "Começar"
                binding.elapsedTimeTextView.text = "Tempo decorrido dos testes: ${formatToDigitalClock(System.currentTimeMillis() - _startTimeInMs)}"
                _executionTimes.clear()
                _timer.cancel()
            } else {
                _hasTestInExecution = true
                _isUnlocking = false
                binding.startStopButton.text = "Parar"
                _success = 0
                _failure = 0
                binding.successTextView.text = "Número de Testes executados com sucesso: $_success"
                binding.failTextView.text = "Número de Testes Executados com Falha: $_failure"
                binding.totalTestsTextView.text = "Número Total de Testes Executados: ${_success + _failure}"
                binding.mediumSuccessTimeTextView.text = "Tempo médio de Execução com sucesso: ${formatToDigitalClock(0)}"
                _startTimeInMs = System.currentTimeMillis()
                _timer = Timer()
                _timer.schedule(
                    object : TimerTask() {
                        override fun run() {
                            val handler = Handler(Looper.getMainLooper())
                            val runnable = Runnable {
                                binding.elapsedTimeTextView.text = "Tempo decorrido dos testes: ${formatToDigitalClock(System.currentTimeMillis() - _startTimeInMs)}"
                            }

                            handler.post(runnable)
                        }
                    },
                    0, 1000
                )
            }
        }
        binding.deviceSerialTextView.text = "Serial do Dispositivo: $_serialBase64"
    }

    public override fun onDestroy() {
        // Unsubscribe to notifications.
        _lkReachableDevice?.unsubscribe(this)
        super.onDestroy()
    }

    /**
     * Method is called whenever new broadcast information is received.
     * @param reachableDevices List of reachable LoopKey devices.
     */
    override fun onReachableDevices(reachableDevices: List<LKCommDevice>) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (reachableDevices.isEmpty()) {
                binding.deviceStatus.text = "Status do Dispositivo: Fora de Alcance"
                return@Runnable
            }
            // Here, we obtain iterate through all the currently reachable devices.
            val devicesIterator = reachableDevices.iterator()

            while (devicesIterator.hasNext()) {
                val device = devicesIterator.next()
                Log.d("LKLIB", device.name ?: "LoopKey Device")
                Log.d("LKLIB", device.rssi.toString() + Base64.encodeToString(device.rollCount, Base64.NO_WRAP))

                // Fill below with serial code of device you are trying to reach.
                if (device.serial.contentEquals(Base64.decode(_serialBase64, Base64.DEFAULT))) {
                    binding.deviceStatus.text = "Status do Dispositivo: Em Alcance"
                    if (_hasTestInExecution && !_isUnlocking) {
                        _isUnlocking = true
                        _communicateUnlockOffline(device)
                    }
                } else {
                    binding.deviceStatus.text = "Status do Dispositivo: Fora de Alcance"
                }
            }
        }

        handler.post(runnable)
    }

    /**
     * Sends unlock message to LoopKey device.
     * @param commDevice The device to communicate with.
     */
    private fun _communicateUnlockOffline(commDevice: LKCommDevice) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            commDevice.adminKey = Base64.decode(_adminKeyBase64, Base64.DEFAULT)
            commDevice.key = Base64.decode(_key64, Base64.DEFAULT)

            val command: LKCommand = createUnlockCommand(object : LKUnlockCommand.Listener {
                override fun onResponse(response: LKUnlockCommand.Response?) {
                    val handler = Handler(Looper.getMainLooper())
                    val runnable = Runnable {
                        _executionTimes.add(System.currentTimeMillis() - _commandStartExecution)
                        _success++
                        binding.successTextView.text = "Número de Testes executados com sucesso: $_success"
                        binding.failTextView.text = "Número de Testes Executados com Falha: $_failure"
                        binding.totalTestsTextView.text = "Número Total de Testes Executados: ${_success + _failure}"
                        binding.mediumSuccessTimeTextView.text = "Tempo médio de Execução com sucesso: ${_executionTimes.sum() / _executionTimes.size}ms"
                        Log.d("LKLIB", "Unlock Status: Success=$_success, Failure=$_failure")
                        _isUnlocking = false
                    }

                    handler.post(runnable)
                }

                override fun onError(error: LKCommand.Error?) {
                    val handler = Handler(Looper.getMainLooper())
                    val runnable = Runnable {
                        _failure++
                        _isUnlocking = false
                        binding.successTextView.text = "Número de Testes executados com sucesso: $_success"
                        binding.failTextView.text = "Número de Testes Executados com Falha: $_failure"
                        binding.totalTestsTextView.text = "Número Total de Testes Executados: ${_success + _failure}"
                        Log.d("LKLIB", "Unlock Status: Success=$_success, Failure=$_failure")
                    }

                    handler.post(runnable)
                }
            })

            command.device = commDevice
            command.userId = 0
            Log.d("LKLIB", "Enqueing Command")
            _commandStartExecution = System.currentTimeMillis()
            LKCommandRunner.instance.enqueue(command)
        }

        handler.post(runnable)
    }

    private fun _requireBluetoothAndLocationPermissionsIfNecessary() {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsAndResults ->
                _lkReachableDevice?.subscribe(this)
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
            _lkReachableDevice?.subscribe(this)
        }

        requestPermissionLauncher.launch(necessaryPermissions.toTypedArray())
    }

    private fun _isPermissionToLocationCoarseGranted(): Boolean {
        return (
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                )
    }

    private fun _isBluetoothPermissionGranted(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
        ) {
            val legacyPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            )
            return legacyPermission == PackageManager.PERMISSION_GRANTED
        } else {
            val connectPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val newBluetoothPermissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            )

            return connectPermission == PackageManager.PERMISSION_GRANTED &&
                    newBluetoothPermissionState == PackageManager.PERMISSION_GRANTED
        }
    }

    fun formatToDigitalClock(mileSeconds: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(mileSeconds).toInt() % 24
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(mileSeconds).toInt() % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(mileSeconds).toInt() % 60

        String.format("%d:%02d:%02d", hours, minutes, seconds)
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            seconds > 0 -> String.format("00:%02d", seconds)
            else -> {
                "00:00"
            }
        }
    }
}
