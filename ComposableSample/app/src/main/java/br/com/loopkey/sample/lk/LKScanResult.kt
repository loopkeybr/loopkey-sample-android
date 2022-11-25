package br.com.loopkey.sample.lk

import android.os.Parcelable
import br.com.loopkey.indigo.lklib.entity.LKCommDevice
import com.ttlock.bl.sdk.api.ExtendedBluetoothDevice
import org.apache.commons.codec.binary.Base32

enum class LKDeviceType {
    LOOPKEY, TTLOCK
}

data class LKScanResult(
    val rssi: Int,
    val kind: LKDeviceType,
    val name: String?,
    val isInDFUMode: Boolean,
    val serial: String?,
    val firmwareVersion: String?,
    val hasLogsToSync: Boolean,
    var macAddress: String?,
    var lockData: String?,
    var userKey: String?,
    var lastSyncTimeStamp: Long,
    val lkCommDevice: LKCommDevice?,
    val ttLockCommDevice: ExtendedBluetoothDevice?,
    val isGateway: Boolean
)
{
    companion object {
        fun map(commDevice: LKCommDevice): LKScanResult
        {
            return LKScanResult(
                commDevice.rssi,
                LKDeviceType.LOOPKEY,
                commDevice.name,
                commDevice.isInDFUMode,
                Base32().encodeToString(commDevice.serial),
                commDevice.firmwareVersion,
                commDevice.hasLogsToSync,
                null,
                null,
                null,
                0,
                commDevice,
                null,
                false
            )
        }

        fun map(commDevice: ExtendedBluetoothDevice, isGateway: Boolean): LKScanResult
        {
            return LKScanResult(
                commDevice.rssi,
                LKDeviceType.TTLOCK,
                commDevice.name,
                commDevice.isDfuMode,
                null,
                null,
                false,
                commDevice.address,
                null,
                null,
                commDevice.date,
                null,
                commDevice,
                isGateway
            )
        }
    }
}
