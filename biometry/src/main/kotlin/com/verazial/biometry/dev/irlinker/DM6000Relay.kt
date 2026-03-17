package com.verazial.biometry.dev.irlinker

import android.content.Context
import android.os.Build
import com.superred.irisalgo.HardwareModel
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.base.RelayDeviceBase
import com.verazial.core.interfaces.RelayDevice
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

class DM6000Relay(
    private val context: Context
) : RelayDeviceBase() {
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val id: String get() = Build.DEVICE
    override val name: String get() = Build.MODEL

    private var manager: HardwareModel? = null
//    private val licenseDirPath =
//        context.getExternalFilesDir(null)!!.absolutePath + "/eyeTracking/"
//    private val hasLicense: Boolean
//        get() = File(licenseDirPath, "license.lic").exists()

    override suspend fun initializeSafe() {
//        if (!hasLicense) error("No license found")
//        AlgManager.getInstance().initAlg(licenseDirPath)

        manager = HardwareModel.getInstance(context)
    }

    override suspend fun turnOnSafe(duration: Duration) {
        manager.notNullOrCommError().setRelayPowerOn(
            duration.inWholeSeconds.toInt(),
            duration.inWholeSeconds.toInt()
        )
    }

    override suspend fun turnOffSafe() {
        manager.notNullOrCommError().setRelayPowerClose()
    }

    override suspend fun closeSafe() {
        manager = null
    }

    override suspend fun stillAliveSafe(): Boolean =
        manager != null


    internal companion object : DeviceProvider(Dispatchers.Main) {
        private const val DEVICE_ID = "rk3288"

        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices(): List<RelayDevice> {
            val deviceID = Build.DEVICE

            if (deviceID != DEVICE_ID) return emptyList()

            return listOf(DM6000Relay(context))
        }
    }
}