package com.verazial.biometry

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.hardware.usb.UsbDevice
import com.juul.kable.Advertisement
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.base.BiometricDeviceReadWriteBase
import com.verazial.biometry.base.RelayDeviceBase
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.interfaces.Device
import com.verazial.core.interfaces.DeviceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext


class IDeviceManager(private val context: Context) :
    DeviceManager() {

    companion object {

        internal var embeddedAratek: BiometricDevice? = null

        internal val deviceProviders: List<DeviceProvider> = listOf(
            com.verazial.biometry.dev.secugen.U20BLE,
            com.verazial.biometry.dev.generic.BackCamera,
            com.verazial.biometry.dev.generic.FrontCamera,
            com.verazial.biometry.dev.pixsur.USBIrisReader,
            //com.verazial.biometry.dev.pixsur.InternalIrisReader,
            com.verazial.biometry.dev.iritech.IrisReader,
            //com.verazial.biometry.dev.identy.FingerprintReader,
            com.verazial.biometry.dev.aratek.FingerprintReader,
            com.verazial.biometry.dev.irlinker.DM6000,
            com.verazial.biometry.dev.irlinker.DM6000Relay,
            com.verazial.biometry.dev.ib.FingerprintReader,
            com.verazial.biometry.dev.suprema.BioMini,
            com.verazial.biometry.dev.generic.NfcDevice,
            com.verazial.biometry.dev.hid.Omnikey5022,
            com.verazial.biometry.dev.elyctis.ElyctisIdReader,
            //com.verazial.biometry.dev.pixsur_gm50.Gm50BaseReader
        )
    }

    init {
        BiometricDeviceBase.settings = settings
        BiometricDeviceReadWriteBase.settings = settings
        RelayDeviceBase.settings = settings
    }

    override fun getDevices(): Flow<List<Device>> = flow {
        val staticDevices = StaticDeviceProviderScope(settings, context).run {
            deviceProviders.flatMap { it.run { getStaticManageableDevices() } }
        }
        println("Static devices: $staticDevices")
        emit(staticDevices)

        val usbDevicesFlow = context.usbDevices()
            .map { usbList ->
                usbList.map { DeviceCandidate.Usb(it) }
            }

        val bleDevicesFlow = bleDevices()
            .map { it.map(DeviceCandidate::Ble) }

        val allDevicesFlow = combine(usbDevicesFlow, bleDevicesFlow) { usbDevices, bleDevices ->
            println("USB devices: $usbDevices")
            println("BLE devices: $bleDevices")
            usbDevices + bleDevices
        }

        allDevicesFlow.map { allDevices ->
            allDevices.mapNotNull { it.findManageableDevice() }.toMutableList()
        }.collect { devices ->

            embeddedAratek?.let { aratek ->
                if (!devices.contains(aratek)) {
                    devices.add(aratek)
                }
            }

            emit(staticDevices + devices)
        }
    }

    private suspend fun DeviceCandidate.findManageableDevice(): Device? {
        val scope = DeviceProviderScope(settings, context, this)
        return deviceProviders.firstNotNullOfOrNull { deviceProvider ->
            deviceProvider.run { scope.getManagedDevice() }
        }
    }

    internal abstract class DeviceProvider(val coroutineContext: CoroutineContext) {
        protected open suspend fun DeviceProviderScope.getManageableDevice(
        ): Device? = null

        suspend fun DeviceProviderScope.getManagedDevice(): Device? =
            withContext(coroutineContext) {
                withTimeoutOrNull(settings.queryTimeout) {
                    getManageableDevice()
                }
            }

        protected open suspend fun StaticDeviceProviderScope.getStaticManagedDevices(
        ): List<Device> = emptyList()

        suspend fun StaticDeviceProviderScope.getStaticManageableDevices(): List<Device> =
            withContext(coroutineContext) {
                withTimeoutOrNull(settings.queryTimeout) {
                    getStaticManagedDevices()
                } ?: emptyList()
            }
    }

    internal data class DeviceProviderScope(
        val settings: Settings,
        val context: Context,
        val deviceCandidate: DeviceCandidate
    )

    internal data class StaticDeviceProviderScope(
        val settings: Settings,
        val context: Context
    )

    sealed class DeviceCandidate {
        data class Usb(val device: UsbDevice) : DeviceCandidate()
        data class Ble(val advertisement: Advertisement) : DeviceCandidate()
        data class Bluetooth(val device: BluetoothDevice) : DeviceCandidate()
    }
}