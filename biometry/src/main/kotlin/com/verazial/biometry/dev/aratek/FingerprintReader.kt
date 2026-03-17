package com.verazial.biometry.dev.aratek

import android.content.Context
import cn.com.aratek.fp.FingerprintImage
import cn.com.aratek.fp.FingerprintScanner
import cn.com.aratek.fp.FingerprintScanner.DEVICE_NOT_OPEN
import cn.com.aratek.fp.FingerprintScanner.DEVICE_REOPEN
import cn.com.aratek.fp.FingerprintScanner.RESULT_OK
import cn.com.aratek.fp.FingerprintScanner.getInstance
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.DeviceProviderScope
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import cn.com.aratek.util.Result as AratekResult

class FingerprintReader(
    private val context: Context,
    override val name: String,
    override val id: String
) : BiometricDeviceBase() {
    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.FINGERPRINT)
    override val coroutineContext: CoroutineContext = Companion.coroutineContext

    private var fingerprintScanner: FingerprintScanner? = null

    override suspend fun initializeSafe() {
        fingerprintScanner = getInstance(context)
        // Ignore result code because it may not need to power on and it will return failure code.
        fingerprintScanner?.powerOn()
        checkingIntCode(RESULT_OK, DEVICE_REOPEN, block = FingerprintScanner::open)
    }

    override suspend fun getMaxSamplesSafe(): Int = 1

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> =
        flow {
            checkingIntCode(block = FingerprintScanner::prepare)

            var result: AratekResult =
                fingerprintScanner?.capture().notNullOrCommError()
            while (result.error != RESULT_OK && currentCoroutineContext().isActive) {
                result = fingerprintScanner?.capture().notNullOrCommError()
            }
            val image = (result.data as FingerprintImage?).notNullOrReadingTimeout()
            val fingerprintImage = image.convert2Png()
            val subtype = targetSamples.first()

            BiometricSample(
                contents = fingerprintImage.base64String,
                type = BiometricSample.Type.FINGER,
                subtype = subtype,
                format = BiometricSample.Type.FINGER.IMAGE,
                quality = -1
            ).let(::listOf)
                .let {
                    emit(BiometricCapture(samples = it))
                }
        }

    override suspend fun stopReadSafe() {
        fingerprintScanner?.finish()
    }

    override suspend fun closeSafe(): Unit = coroutineScope {
        if (fingerprintScanner == null) return@coroutineScope
        launch {
            fingerprintScanner?.finish()
            checkingIntCode(RESULT_OK, DEVICE_NOT_OPEN, block = FingerprintScanner::close)
            // Ignore result code because it may not need to power off and it will return failure code.
            fingerprintScanner?.powerOff()
        }.invokeOnCompletion { fingerprintScanner = null }
    }

    override suspend fun stillAliveSafe(): Boolean =
        fingerprintScanner != null


    private inline fun checkingIntCode(
        vararg validCodes: Int = intArrayOf(RESULT_OK),
        block: FingerprintScanner.() -> Int
    ): Unit = runCatching {
        val result = fingerprintScanner?.block().notNullOrCommError()
        if (result !in validCodes) return@runCatching null
        Unit
    }.getOrNull().notNullOrCommError()

    internal companion object : DeviceProvider(Dispatchers.IO) {
        override suspend fun DeviceProviderScope.getManageableDevice(): BiometricDevice? {
            if (deviceCandidate !is IDeviceManager.DeviceCandidate.Usb) return null

            val usb = deviceCandidate.device
            val isValid = (usb.vendorId == 11388 && usb.productId == 2305) ||
                    (usb.vendorId == 10477 && usb.productId == 8255) ||
                    (usb.vendorId == 10477 && usb.productId == 8228)
            if (!isValid) return null

            // --- INICIO MAGIA DE ARATEK ---
            // Cargamos la configuración oculta para que el SDK encuentre el lector interno
            runCatching {
                context.assets.open("terminal.xml").use { inputStream ->
                    cn.com.aratek.dev.Terminal.loadSettings(inputStream)
                    android.util.Log.d("TEST_HUELLA", "ÉXITO: terminal.xml cargado en el SDK de Aratek")
                }
            }.onFailure {
                android.util.Log.e("TEST_HUELLA", "ERROR: No se pudo cargar terminal.xml. ¿Está en la carpeta assets? -> ${it.message}")
            }
            // --- FIN MAGIA DE ARATEK ---

            android.util.Log.d("TEST_HUELLA", "Filtro pasado. Intentando instanciar...")
            val instance = runCatching { getInstance(context) }.getOrNull()

            if (instance == null) {
                android.util.Log.e("TEST_HUELLA", "Error al crear la instancia de Aratek")
                return null
            }

            val ok = runCatching {
                instance.powerOn()
                val result = instance.open()
                android.util.Log.d("TEST_HUELLA", "Resultado de open(): $result")
                result == RESULT_OK
            }.getOrNull() ?: false

            if (!ok) {
                android.util.Log.e("TEST_HUELLA", "Error: powerOn o open fallaron. ¿Faltan permisos USB?")
                return null
            }

            val model = instance.model.data as? String ?: return null
            val serial = instance.serial.data as? String ?: return null

            instance.close()

            val device = FingerprintReader(context, model, serial)
            IDeviceManager.embeddedAratek = device
            return device
        }
    }
}