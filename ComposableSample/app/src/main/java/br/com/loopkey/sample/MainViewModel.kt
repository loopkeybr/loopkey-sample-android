package br.com.loopkey.sample

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.loopkey.sample.lk.LKScanner
import br.com.loopkey.sample.lk.LKScanResult
import br.com.loopkey.sample.lk.LKScannerProtocol
import br.com.loopkey.sample.lk.LKUnlockDeviceInteractor
import kotlinx.coroutines.launch

class MainViewModel(private val _context: Context): ViewModel(), LKScannerProtocol
{
    val renderedDevices: SnapshotStateList<LKScanResult> =  mutableStateListOf()
    private lateinit var _scanner: LKScanner
    private lateinit var _unlockInteractor: LKUnlockDeviceInteractor

    // Lockers Properties
    val serialBase32: String = "LLKFAABAAADABO33"
    val userKey: String = "Ey0tqqG2L3piAiwDCPGDOopEewIk+olABgU1xBAsJv8="
    val lockData: String = "DUYzOJDhNGPt1NFVVUgE7yROijIeXg/Hh1HeZs0fJLvCZToZF7VLhWPMxn2ZhgnQPYyq71bzdOenQLnkFKIefP4VgaMavAp+D7TJQ6JcT7FUR5p2XUfcIh3gJ+P3BdEeg4R1qGx3km8Fw9moudswGbpjm88Z7YMHhaoJdVWjBZQK4QMDW8LP9Fq2QkvMwIBbui/LNsc1+b5b8GV2eQ7q3BJqesPuPI2CfIXnBBSZD9txIAINSBfTkqui/kj7XzL8NwP4cQ+sqQLCAyajstSezNkUCpC0+likoikBoFmcyQIC5hOwnOlSFZEy/J2IiSApqo7rZtLETKdZDuBiblmdn5WEplEH/mCO3z4C+s7yryar4hUWkfJUh55t4+63ddUWd6ZA/ArP3zawGtfjGf+iqunVScZAI9MaJ9CK14e5CZnVrSurAEky6HCMAGj6CWbqd43F+y74dKDx5e0YGYANzKn6aJTVs4k840awb9OaVzVGcnZmVRAIJExpRNF53BBun4n6ACm3APyJCpY3dY8k/Ck9RVqSyLZ3S0zTR0J/9tdk+jJYaO38Eo1hRseBxjli4cPXdKexrfL5ev/fdgmUZc/dRYEao36XZWY6vuf/eT7Ft7xYylHCmquDL5JLwM/pq/TkWus0br6MiKLcgLRVcX31SFpzy7S5edsmNSaFBKaByqoBGDZHPzjvJvCnh6Y65pX6MnCoar/4c6jMGW++v2vTX7Vcqg=="
    val lockMac: String = "6B:D3:5F:B5:5C:AA"

    override fun didUpdateVisible(visibleDevices: List<LKScanResult>)
    {
        viewModelScope.launch {
            renderedDevices.clear()
            renderedDevices.addAll(visibleDevices)
        }
    }

    fun unlockTapped(device: LKScanResult) {
        if (device.macAddress != lockMac && device.serial != serialBase32) {
            Log.d("UNLOCK", "Unlock requested to unknown device properties")
        }

        // Both calls are equivalent. You can use which one you prefer.
//        device.lockData = lockData
//        device.macAddress = lockMac
//        device.userKey = userKey
//
//        _unlockInteractor.unlock(device) { result ->
//
//        }

        _unlockInteractor.unlock(serialBase32 = serialBase32,
            lockMac = lockMac,
            userKey = userKey,
            lockData = lockData) { result ->
        }
    }

    fun onPermissionsGranted()
    {
        this._unlockInteractor = LKUnlockDeviceInteractor(context = _context)
        this._scanner = LKScanner(context = _context)
        _scanner.subscribe(this)
    }
}

