package com.verazial.biometry.dev.identy

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Base64
/*import com.identy.Attempt
import com.identy.ERRORS
import com.identy.FingerAS
import com.identy.IdentyError
import com.identy.IdentyResponse
import com.identy.IdentyResponseListener
import com.identy.IdentySdk
import com.identy.QualityMode
import com.identy.TemplateSize
import com.identy.enums.Finger
import com.identy.enums.FingerDetectionMode
import com.identy.enums.Hand
import com.identy.enums.Template*/
import com.verazial.biometry.BuildConfig
import com.verazial.biometry.IDeviceManager
import com.verazial.biometry.base.BiometricDeviceBase
import com.verazial.core.error.DeviceCommunicationError
import com.verazial.core.error.ReadingTimeout
import com.verazial.core.interfaces.BiometricDevice
import com.verazial.core.model.BiometricCapture
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricTechnology
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

class FingerprintReader : BiometricDeviceBase() {
    override val biometricTechnologies: Set<BiometricTechnology> = setOf(BiometricTechnology.FINGERPRINT)
    override val coroutineContext: CoroutineContext = Companion.coroutineContext
    override val id: String = "IDENTY_fingerprint_reader"
    override val name: String = "IDENTY"

    private var isInitialized = false
    private var isReading = false


    override suspend fun initializeSafe() {
        isInitialized = true
    }

    override suspend fun getMaxSamplesSafe(): Int = 4

    override suspend fun performReadSafe(
        preferredFormats: List<BiometricSample.Type.Format>,
        targetSamples: List<BiometricSample.Type.Subtype>,
        binder: BiometricDeviceViewBinder?,
        enablePreviews: Boolean
    ): Flow<BiometricCapture> = flow {
        /*runCatching {
            check(isInitialized) { "Device is not initialized" }
            check(!isReading) { "Device is already reading" }
            isReading = true
            require(binder is PreviewViewBinder) { "PreviewViewBinder is required" }
            require(BiometricSample.Type.FINGER.UNKNOWN !in targetSamples)
            { "Unknown finger type is not supported" }

            val (sampleFormat, identyFormat) = preferredFormats.bestFormat(
                formatMappings = mapOf(
                    BiometricSample.Type.FINGER.ISO_IEC_19794_4_2005 to Template.ISO_19794_4,
                    BiometricSample.Type.FINGER.ISO_IEC_19794_2_2005 to Template.ISO_19794_2,
                    BiometricSample.Type.FINGER.IMAGE to Template.PNG,
                ),
                defaultFormat = BiometricSample.Type.FINGER.IMAGE
            )

            val samples = suspendCancellableCoroutine { cont ->
                IdentySdk.newInstance(
                    binder.activity(),
                    binder.licenseFileName,
                    { it.configSdk(identyFormat, targetSamples, binder.timeout); it.capture() },
                    object : IdentyResponseListener {
                        override fun onAttempt(p0: Hand?, p1: Int, p2: MutableMap<Finger, Attempt>?) {
                        }

                        override fun onResponse(response: IdentyResponse, hashSet: HashSet<String>?) {
                            cont.resume(response.getBiometricSamples(sampleFormat, identyFormat))
                        }

                        override fun onErrorResponse(error: IdentyError, hashSet: HashSet<String>?) {
                            when (error.error) {
                                ERRORS.ACTIVITY_PAUSED,
                                ERRORS.ACTIVITY_PAUSED_ON_BACK_PRESSED,
                                ERRORS.USER_CANCELLED_ON_NEXT_DETECTION,
                                ERRORS.TIMED_OUT -> cont.resumeWithException(ReadingTimeout)

                                else -> cont.resumeWithException(
                                    DeviceCommunicationError(
                                        message = "IDENTY error: ${error.error}"
                                    )
                                )
                            }
                        }
                    },
                    false,
                    false
                )
            }

            samples
        }.also { isReading = false }.getOrThrow()
            .let { emit(BiometricCapture(samples = it)) }*/
    }

    override suspend fun stopReadSafe() = Unit //TODO: Can not stop reading as representatives said

    override suspend fun closeSafe() {
        isInitialized = false
    }

    override suspend fun stillAliveSafe(): Boolean = isInitialized

    /*private fun IdentySdk.configSdk(
        sampleFormat: Template,
        targetSamplesRaw: List<BiometricSample.Type.Subtype>,
        timeout: Duration
    ) {
        val targetSamples = targetSamplesRaw.toMutableList()

        if (targetSamples.singleOrNull() == BiometricSample.Type.FINGER.UNKNOWN)
            targetSamples[0] = BiometricSample.Type.FINGER.RIGHT_INDEX_FINGER

        val fingerDetectionMode = targetSamples.map {
            when (it) {
                BiometricSample.Type.FINGER.RIGHT_INDEX_FINGER -> FingerDetectionMode.RIGHT_INDEX
                BiometricSample.Type.FINGER.RIGHT_MIDDLE_FINGER -> FingerDetectionMode.RIGHT_MIDDLE
                BiometricSample.Type.FINGER.RIGHT_RING_FINGER -> FingerDetectionMode.RIGHT_RING
                BiometricSample.Type.FINGER.RIGHT_LITTLE_FINGER -> FingerDetectionMode.RIGHT_LITTLE
                BiometricSample.Type.FINGER.RIGHT_THUMB -> FingerDetectionMode.RIGHT_THUMB
                BiometricSample.Type.FINGER.LEFT_INDEX_FINGER -> FingerDetectionMode.LEFT_INDEX
                BiometricSample.Type.FINGER.LEFT_MIDDLE_FINGER -> FingerDetectionMode.LEFT_MIDDLE
                BiometricSample.Type.FINGER.LEFT_RING_FINGER -> FingerDetectionMode.LEFT_RING
                BiometricSample.Type.FINGER.LEFT_LITTLE_FINGER -> FingerDetectionMode.LEFT_LITTLE
                BiometricSample.Type.FINGER.LEFT_THUMB -> FingerDetectionMode.LEFT_THUMB
                else -> error("Unsupported finger type: $it")
            }
        }.toMutableList()
        val rightFingers = listOf(
            FingerDetectionMode.RIGHT_INDEX,
            FingerDetectionMode.RIGHT_MIDDLE,
            FingerDetectionMode.RIGHT_RING,
            FingerDetectionMode.RIGHT_LITTLE
        )
        val leftFingers = listOf(
            FingerDetectionMode.LEFT_INDEX,
            FingerDetectionMode.LEFT_MIDDLE,
            FingerDetectionMode.LEFT_RING,
            FingerDetectionMode.LEFT_LITTLE
        )
        fingerDetectionMode.run {
            if (containsAll(rightFingers)) {
                removeAll(rightFingers)
                add(FingerDetectionMode.R4F)
            }
            if (containsAll(leftFingers)) {
                removeAll(leftFingers)
                add(FingerDetectionMode.L4F)
            }
        }

        setDetectionMode(fingerDetectionMode.toTypedArray())
        setRequiredTemplates(
            hashMapOf(
                sampleFormat to hashMapOf(
                    Finger.THUMB to arrayListOf(TemplateSize.DEFAULT),
                    Finger.INDEX to arrayListOf(TemplateSize.DEFAULT),
                    Finger.MIDDLE to arrayListOf(TemplateSize.DEFAULT),
                    Finger.RING to arrayListOf(TemplateSize.DEFAULT),
                    Finger.LITTLE to arrayListOf(TemplateSize.DEFAULT)
                )
            )
        )
        isCalculateNFIQ = false
        setAsSecMode(FingerAS.BALANCED_HIGH)
        displayResult(false)
        base64EncodingFlag = Base64.NO_WRAP
        displayImages(false)
        setDebug(BuildConfig.DEBUG)
        setAllowHandChange(false)
        setDisplayBoxes(false)
        setAllowVerificationAfterSpoof(false)
        disableQC()
        this.setAttemptsTimeout(Int.MAX_VALUE, timeout.inWholeSeconds.toInt())
        //disableDisplayTransactionAlerts()
        setQualityMode(QualityMode.VERIFICATION)
        isAssistedMode = false
    }*/

    /*private fun IdentyResponse.getBiometricSamples(
        format: BiometricSample.Type.Format,
        identyFormat: Template
    ): List<BiometricSample> =
        prints.map { (position, output) ->
            val hand = position.first
            val finger = position.second
            val sampleFinger = when (hand) {
                Hand.RIGHT -> when (finger) {
                    Finger.INDEX -> BiometricSample.Type.FINGER.RIGHT_INDEX_FINGER
                    Finger.MIDDLE -> BiometricSample.Type.FINGER.RIGHT_MIDDLE_FINGER
                    Finger.RING -> BiometricSample.Type.FINGER.RIGHT_RING_FINGER
                    Finger.LITTLE -> BiometricSample.Type.FINGER.RIGHT_LITTLE_FINGER
                    Finger.THUMB -> BiometricSample.Type.FINGER.RIGHT_THUMB
                    else -> BiometricSample.Type.FINGER.UNKNOWN
                }

                Hand.LEFT -> when (finger) {
                    Finger.INDEX -> BiometricSample.Type.FINGER.LEFT_INDEX_FINGER
                    Finger.MIDDLE -> BiometricSample.Type.FINGER.LEFT_MIDDLE_FINGER
                    Finger.RING -> BiometricSample.Type.FINGER.LEFT_RING_FINGER
                    Finger.LITTLE -> BiometricSample.Type.FINGER.LEFT_LITTLE_FINGER
                    Finger.THUMB -> BiometricSample.Type.FINGER.LEFT_THUMB
                    else -> BiometricSample.Type.FINGER.UNKNOWN
                }

                else -> BiometricSample.Type.FINGER.UNKNOWN
            }

            BiometricSample(
                contents = output.templates[identyFormat]?.get(TemplateSize.DEFAULT)
                    .notNullOrCommError(),
                type = BiometricSample.Type.FINGER,
                subtype = sampleFinger,
                format = format,
                quality = 0
            )
        }*/


    internal companion object : IDeviceManager.DeviceProvider(Dispatchers.Main) {
        override suspend fun IDeviceManager.StaticDeviceProviderScope.getStaticManagedDevices(): List<BiometricDevice> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager?
                ?: return emptyList()
            val hasBackCamera = manager.cameraIdList.any {
                manager.getCameraCharacteristics(it).run {
                    // There must be a back camera with flash
                    get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
                            && get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }
            if (!hasBackCamera) return emptyList()

            return listOf(FingerprintReader())
        }
    }
}

data class PreviewViewBinder(
    val licenseFileName: String,
    val timeout: Duration,
    val activity: () -> Activity
) : BiometricDevice.BiometricDeviceViewBinder