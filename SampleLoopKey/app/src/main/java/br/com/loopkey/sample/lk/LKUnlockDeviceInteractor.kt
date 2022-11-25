package br.com.loopkey.sample.lk

import android.content.Context
import android.util.Base64
import br.com.loopkey.indigo.lklib.commands.LKCommand
import br.com.loopkey.indigo.lklib.commands.LKCommandRepository
import br.com.loopkey.indigo.lklib.commands.LKCommandRunner
import br.com.loopkey.indigo.lklib.commands.implementations.LKGetBatteryCommand
import br.com.loopkey.indigo.lklib.commands.implementations.LKUnlockCommand
import br.com.loopkey.indigo.lklib.entity.LKBattery
import com.ttlock.bl.sdk.api.TTLockClient
import com.ttlock.bl.sdk.callback.ControlLockCallback
import com.ttlock.bl.sdk.constant.ControlAction
import com.ttlock.bl.sdk.entity.ControlLockResult
import com.ttlock.bl.sdk.entity.LockError
import kotlinx.coroutines.launch

enum class LKUnlockResult
{
    success, signalError, syncError, timeout, permissionError, doorUnavailable, messageSignError,
    serverCommError, lockDataMissing, unknown
}

class LKUnlockDeviceInteractor(context: Context): LKScannerProtocol
{
    private val scanner: LKScanDevices = LKScanDevices(context)
    private val _ttClient = TTLockClient.getDefault()

    init
    {
        scanner.subscribe(this)
    }

    fun unlock(device: LKScanResult, handler: (LKUnlockResult) -> Unit)
    {
        when (device.kind) {
            LKDeviceType.LOOPKEY -> openLK(device, handler)
            LKDeviceType.TTLOCK -> openTT(device, handler)
        }
    }

    fun unlock(serialBase32: String,
               lockMac: String?,
               userKey: String?,
               lockData: String?,
               handler: (LKUnlockResult) -> Unit)
    {
        val device = scanner.get(serialBase32, lockMac) ?: return handler.invoke(LKUnlockResult.doorUnavailable)

        device.lockData = lockData
        device.macAddress = lockMac
        device.userKey = userKey

        unlock(device, handler)
    }

    fun openTT(device: LKScanResult, handler: (LKUnlockResult) -> Unit)
    {
        val lockData = device.lockData
        val lockMac = device.macAddress

        if (lockData != null && lockMac != null) {
            _ttClient.controlLock(
                ControlAction.UNLOCK,
                lockData,
                lockMac,
                object : ControlLockCallback {
                    override fun onControlLockSuccess(controlLockResult: ControlLockResult)
                    {
                        handler.invoke(LKUnlockResult.success)
                    }

                    override fun onFail(error: LockError) {
                        when (error) {
                            LockError.DEVICE_CONNECT_FAILED,
                            LockError.LOCK_CONNECT_FAIL -> {
                                handler.invoke(LKUnlockResult.doorUnavailable)
                            }
                            LockError.LOCK_NO_PERMISSION -> {
                                handler.invoke(LKUnlockResult.permissionError)
                            }
                            else -> {
                                handler.invoke(LKUnlockResult.unknown)
                            }
                        }
                    }
                }
            )
        }
    }

    fun openLK(device: LKScanResult, handler: (LKUnlockResult) -> Unit)
    {
        val command: LKCommand = LKCommandRepository.createUnlockCommand(object : LKUnlockCommand.Listener {
            override fun onResponse(response: LKUnlockCommand.Response?) {
                when (response) {
                    LKUnlockCommand.Response.UNLOCKED,
                    LKUnlockCommand.Response.ALREADY_UNLOCKED -> handler(LKUnlockResult.success)
                    LKUnlockCommand.Response.ALREADY_OPEN -> handler(LKUnlockResult.success)
                    LKUnlockCommand.Response.SYNC_ISSUE -> handler.invoke(LKUnlockResult.syncError)
                    else -> handler.invoke(LKUnlockResult.unknown)
                }
            }
            override fun onError(error: LKCommand.Error?)
            {
                when (error) {
                    LKCommand.Error.SERVER_COMM_ERROR -> handler.invoke(LKUnlockResult.serverCommError)
                    LKCommand.Error.UNAUTHORIZED -> handler.invoke(LKUnlockResult.permissionError)
                    else -> handler.invoke(LKUnlockResult.unknown)
                }
            }
        })

        device.userKey.let { userKey ->
            if (userKey != null) {
                device.lkCommDevice?.key = Base64.decode(userKey, Base64.DEFAULT)
            }
            if (device.lkCommDevice != null) {
                command.device = device.lkCommDevice
            }
            command.userId = 0

            LKCommandRunner.instance.enqueue(command)
        }
    }
    override fun didUpdateVisible(visibleDevices: List<LKScanResult>)
    {
    }
}