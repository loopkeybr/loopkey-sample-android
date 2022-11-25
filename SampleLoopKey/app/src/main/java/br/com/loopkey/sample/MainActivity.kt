package br.com.loopkey.sample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import br.com.loopkey.sample.databinding.ActivityMainBinding
import br.com.loopkey.sample.lk.LKScanDevices
import br.com.loopkey.sample.lk.LKScanResult
import br.com.loopkey.sample.lk.LKScannerProtocol
import br.com.loopkey.sample.lk.LKUnlockDeviceInteractor
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity(), LKScannerProtocol {
    private var _lkReachableDevice: LKReachableDevice? = null

    private var _isUnlocking = false

    private var _success: Int = 0

    private var _failure: Int = 0

    private lateinit var binding: ActivityMainBinding

    private var _hasTestInExecution: Boolean = false

    private var _serialBase32: String = "LLKFAACAAACAAHFY"

    private var _key64: String = "yTUmH2/Mq/8wyhz5ritkhA9dDxIbtkYhLPO/5SdYWJw="

    private var _lockData: String = "DUYzOJDhNGPt1NFVVUgE7yROijIeXg/Hh1HeZs0fJLvCZToZF7VLhWPMxn2ZhgnQPYyq71bzdOenQLnkFKIefP4VgaMavAp+D7TJQ6JcT7FUR5p2XUfcIh3gJ+P3BdEeg4R1qGx3km8Fw9moudswGbpjm88Z7YMHhaoJdVWjBZQK4QMDW8LP9Fq2QkvMwIBbui/LNsc1+b5b8GV2eQ7q3BJqesPuPI2CfIXnBBSZD9txIAINSBfTkqui/kj7XzL8NwP4cQ+sqQLCAyajstSezNkUCpC0+likoikBoFmcyQIC5hOwnOlSFZEy/J2IiSApqo7rZtLETKdZDuBiblmdn5WEplEH/mCO3z4C+s7yryar4hUWkfJUh55t4+63ddUWd6ZA/ArP3zawGtfjGf+iqunVScZAI9MaJ9CK14e5CZnVrSurAEky6HCMAGj6CWbqd43F+y74dKDx5e0YGYANzKn6aJTVs4k840awb9OaVzVGcnZmVRAIJExpRNF53BBun4n6ACm3APyJCpY3dY8k/Ck9RVqSyLZ3S0zTR0J/9tdk+jJYaO38Eo1hRseBxjli4cPXdKexrfL5ev/fdgmUZc/dRYEao36XZWY6vuf/eT7Ft7xYylHCmquDL5JLwM/pq/TkWus0br6MiKLcgLRVcX31SFpzy7S5edsmNSaFBKaByqoBGDZHPzjvJvCnh6Y65pX6MnCoar/4c6jMGW++v2vTX7Vcqg=="

    private var _lockMac: String = "" //"6B:D3:5F:B5:5C:AA"

    private var _startTimeInMs: Long = 0

    private var _commandStartExecution: Long = 0

    private var _timer: Timer = Timer()

    private var _executionTimes: MutableList<Long> = mutableListOf()

    private lateinit var unlockInteractor: LKUnlockDeviceInteractor
    private lateinit var scanner: LKScanDevices

    public override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // LoopKey library initialization.
        LKLib.install(this.applicationContext)

        // Get reference to device scanner.
        _lkReachableDevice = LKReachableDeviceImpl.INSTANCE

        _requireBluetoothAndLocationPermissionsIfNecessary()
        binding.deviceStatus.text = "Status do Dispositivo: Fora de Alcance"

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

        binding.deviceSerialTextView.text = "Serial do Dispositivo: $_serialBase32"
    }

    public override fun onDestroy() {
        // Unsubscribe to notifications.
        super.onDestroy()
    }

    /**
     * Method is called whenever new broadcast information is received.
     * @param reachableDevices List of reachable LoopKey devices.
     */
    override fun didUpdateVisible(visibleDevices: List<LKScanResult>)
    {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (visibleDevices.isEmpty()) {
                binding.deviceStatus.text = "Status do Dispositivo: Fora de Alcance"
                return@Runnable
            }
            val device = visibleDevices.firstOrNull { element -> element.serial == _serialBase32 }

                // Fill below with serial code of device you are trying to reach.
                if (device != null) {
                    binding.deviceStatus.text = "Status do Dispositivo: Em Alcance"
                    if (_hasTestInExecution && !_isUnlocking) {
                        _isUnlocking = true
                        _communicateUnlockOffline(device)
                    }
                } else {
                    binding.deviceStatus.text = "Status do Dispositivo: Fora de Alcance"
                }
            }

        handler.post(runnable)
    }

    /**
     * Sends unlock message to LoopKey device.
     * @param commDevice The device to communicate with.
     */
    private fun _communicateUnlockOffline(commDevice: LKScanResult)
    {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            commDevice.userKey = _key64
            commDevice.lockData = _lockData
            commDevice.macAddress = _lockMac

            unlockInteractor.unlock(commDevice, { result ->

            })
        }

        handler.post(runnable)
    }

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
