package br.com.loopkey.sample.lk

import android.content.Context
import android.util.Log
import br.com.loopkey.indigo.lklib.LKLib
import br.com.loopkey.indigo.lklib.core.LKReachableDevice
import br.com.loopkey.indigo.lklib.core.LKReachableDeviceImpl
import br.com.loopkey.indigo.lklib.core.LKReachableDeviceListener
import br.com.loopkey.indigo.lklib.entity.LKCommDevice
import com.ttlock.bl.sdk.api.ExtendedBluetoothDevice
import com.ttlock.bl.sdk.api.TTLockClient
import com.ttlock.bl.sdk.callback.ScanLockCallback
import com.ttlock.bl.sdk.entity.LockError
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LKScanDevices(context: Context): LKReachableDeviceListener, ScanLockCallback, CoroutineScope
{
    // Scanners
    private val _ttClient: TTLockClient
    private val _lkScanner: LKReachableDevice

    // Class Properties
    private val visibleDevices: MutableList<LKScanResult> = mutableListOf()
    private val _listeners: MutableList<LKScannerProtocol> = mutableListOf()
    private var _timerJob: Job? = null

    // Coroutines Support
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job
    private val _mutex = Mutex()

    init
    {
        _ttClient = TTLockClient.getDefault()
        _ttClient.prepareBTService(context)

        LKLib.install(context)
        _lkScanner = LKReachableDeviceImpl.INSTANCE
    }

    fun subscribe(listener: LKScannerProtocol) = runBlocking {
        _mutex.withLock {
            val oldSize = _listeners.size

            if (!_listeners.contains(listener)) {
                _listeners.add(listener)

                listener.didUpdateVisible(visibleDevices.toList())
            }

            if (oldSize == 0) {
                _startScan()
            }
        }
    }

    fun unsubscribe(listener: LKScannerProtocol) = runBlocking {
        _mutex.withLock {
            _listeners.remove(listener)

            if (_listeners.size == 0) {
                _stopScan()

                _timerJob?.cancel()
            }
        }
    }

    fun get(serial: String, lockMac: String?): LKScanResult? = runBlocking {
        _mutex.withLock {
            return@runBlocking visibleDevices.firstOrNull {
                it.serial == serial || it.macAddress == lockMac
            }
        }
    }

    @Synchronized
    override fun onReachableDevices(reachableDevices: List<LKCommDevice>)
    {
        launch(job) {
            _mutex.withLock {
                Log.d("LK", "LKDevices Found $reachableDevices")
                visibleDevices.removeAll { e -> e.kind ==  LKDeviceType.LOOPKEY }
                visibleDevices.addAll(reachableDevices.map { LKScanResult.map(it) })
            }

            _notifyListeners()
        }
    }

    private fun _startScan()
    {
        _lkScanner.subscribe(this)
        _ttClient.startScanLock(this)

        _checkForUnreachableTTLockDevices()
    }

    private fun _stopScan()
    {
        _lkScanner.unsubscribe(this)
        _ttClient.stopScanLock()
    }

    /**
     * Notifies the listeners with the given collection of bluetooth models of LK reachable devices.
     * @param bluetoothModels The models to be passed to the listeners.
     */
    private fun _notifyListeners() = runBlocking {
        _mutex.withLock {
            for (listener in _listeners) {
                Log.d("Scanner", "Notifying ${listener.javaClass} about visible devices.")
                listener.didUpdateVisible(visibleDevices)
            }
        }
    }

    override fun onFail(error: LockError?)
    {
        print("Failed to Scan: ${error?.description}")
    }

    override fun onScanLockSuccess(device: ExtendedBluetoothDevice?)
    {
        Log.d("TTLOCK", "TTDevice Found = ${device?.name}")
        val ttDevice = device ?: return
        launch {
            var needsToNotify = false

            _mutex.withLock {
                val scanResult = LKScanResult.map(ttDevice, false)
                val indexOfTTDeviceOnList = visibleDevices.indexOfFirst {
                    it.ttLockCommDevice?.address == device.address
                }

                if (indexOfTTDeviceOnList == -1) {
                    this@LKScanDevices.visibleDevices.add(scanResult)
                    needsToNotify = true
                } else {
                    this@LKScanDevices.visibleDevices
                        .elementAtOrNull(indexOfTTDeviceOnList)?.ttLockCommDevice?.date = device.date
                }
            }

            if (needsToNotify) {
                _notifyListeners()
            }
        }
    }

    private fun _checkForUnreachableTTLockDevices()
    {
        _timerJob = _tickerFlow(7000)
            .onEach {
                Log.d("TTLOCK", "Checking for Unreachable TTLock Devices")
                launch(job) {
                    var elementsWasRemoved = false
                    _mutex.withLock {
                        elementsWasRemoved = this@LKScanDevices.visibleDevices.removeAll {
                            it.kind == LKDeviceType.TTLOCK && (System.currentTimeMillis() - (it.ttLockCommDevice?.date ?: 0)) > 10000
                        }
                    }

                    if (elementsWasRemoved) {
                        Log.d("TTLOCK", "TTLock Elements Removed, notifying Listeners")
                        _notifyListeners()
                    }
                }
            }
            .launchIn(this)
    }

    private fun _tickerFlow(period: Long, initialDelay: Long = 1000) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }
}