/*package com.verazial.biometry.dev.pixsur_gm50

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.TextureView
import cn.com.pixsur.gmfaceiris.EnrollHandler
import cn.com.pixsur.gmfaceiris.GmSDK
import cn.com.pixsur.gmfaceiris.sensetime.FaceHandler
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.biometry.IDeviceManager.DeviceProvider
import com.verazial.biometry.IDeviceManager.StaticDeviceProviderScope
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.interfaces.Device
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

abstract class Gm50BaseReader(
    protected val context: Context
) : BiometricDeviceBase() {

    override val coroutineContext: CoroutineContext = Dispatchers.Main

    protected var handler: EnrollHandler? = null
    private var isInitialized = false

    private val faceInitListener = object : FaceHandler.OnFaceSDKInitListener {
        override fun onFaceSDKActiveSuccess(
            stRes: Long,
            errStMsg: String,
            bdRes: Long,
            errBdMsg: String
        ) {
            Log.d("KonektorService", "FaceSDK Active: $stRes, $errStMsg, $bdRes, $errBdMsg")
        }

        override fun onFaceSDKInitSuccess(success: Int) {
            Log.d("KonektorService", "FaceSDK Init Success: $success")
        }
    }

    override suspend fun initializeSafe() {
        if(handler != null) return
        // This is needed for motor movement
        GmSDK.faceHandler.registerFaceSDKInitListener(faceInitListener)
        GmSDK.faceHandler.start( context, "" )
        handler = EnrollHandler()
        isInitialized = true
    }

    override suspend fun stillAliveSafe(): Boolean {
        return isInitialized && handler != null
    }

    override suspend fun stopReadSafe() {
        handler?.apply {
            try { stop() } catch (_: Throwable) {}
            try { setEnrollListener(null) } catch (_: Throwable) {}
        }
        handler = null
        isInitialized = false
    }

    override suspend fun closeSafe() {
        handler?.apply {
            try { stop() } catch (_: Throwable) {}
            try { setEnrollListener(null) } catch (_: Throwable) {}
        }
        handler = null
        isInitialized = false
    }

    protected fun requireHandler(): EnrollHandler =
        handler ?: throw DeviceCommunicationError("GM50 not initialized")

    internal companion object : DeviceProvider(Dispatchers.IO) {
        override suspend fun StaticDeviceProviderScope.getStaticManagedDevices(): List<Device> {
            return listOf(
                Gm50FaceReader(context),
                Gm50IrisReader(context)
            )
        }
    }
}

class PixsurGm50ViewBinder(
    val activity: Activity,
    val textureView: TextureView
) : BiometricDevice.BiometricDeviceViewBinder {

    fun attachTo(handler: EnrollHandler) {
    }

    fun detachFrom(handler: EnrollHandler) {
    }
}
*/