package com.verazial.biometry_test.tests

import com.verazial.biometry.dev.identy.FingerprintReader
import com.verazial.biometry.dev.identy.PreviewViewBinder
//import com.verazial.biometry.dev.pixsur.InternalIrisReader
import com.verazial.biometry.dev.pixsur.USBIrisReader
/*import android.view.TextureView
import android.view.ViewGroup
import com.verazial.biometry.dev.pixsur_gm50.Gm50FaceReader
import com.verazial.biometry.dev.pixsur_gm50.Gm50IrisReader
import com.verazial.biometry.dev.pixsur_gm50.PixsurGm50ViewBinder*/
import com.verazial.biometry.dev.secugen.U20BLE
import com.verazial.biometry_test.tests.model.BioDevSpecs
import com.verazial.core.model.BiometricSample
import com.verazial.core.model.BiometricSample.Type.FINGER
import com.verazial.core.model.BiometricSample.Type.IRIS
import com.verazial.core.model.BiometricTechnology
import kotlin.time.Duration.Companion.seconds


val Secugen_U20BLE = BioDevSpecs(
    friendlyName = "Secugen U20BLE",
    readingTimeout = 5.seconds,
    deviceKClass = U20BLE::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "CC:35:5A:00:00:CD",
    deviceName = "Unity20BT-BLE-0205",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.ANSI_INCITS_378_2004
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Pixsur_USBIrisReader = BioDevSpecs(
    friendlyName = "Pixsur USB Iris Reader",
    readingTimeout = 10.seconds,
    deviceKClass = USBIrisReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.IRIS),
    deviceId = "4660:257",
    deviceName = "IRIS-SCANNER",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 2,
    deviceCapableFormats = listOf(
        IRIS.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = true,
    providesDistanceStatus = true
)

/*val Pixsur_InternalIrisReader = BioDevSpecs(
    friendlyName = "Pixsur Internal Iris Reader",
    readingTimeout = 5.seconds,
    deviceKClass = InternalIrisReader::class,
    deviceBiometricTechnology = BiometricTechnology.IRIS,
    deviceId = "UVC0",
    deviceName = "Pixsur Internal",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 2,
    deviceCapableFormats = listOf(
        IRIS.IMAGE
    ),
    deviceBinder = null

 providesPreviews = false),
 reportsDistanceStatus = false*/

val Iritech_IrisReader_Mono = BioDevSpecs(
    friendlyName = "Iritech Iris Reader Monocular",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.iritech.IrisReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.IRIS),
    deviceId = "8035:61441",
    deviceName = "IriShield",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        IRIS.IMAGE,
        IRIS.ISO_IEC_19794_6_2005
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Iritech_IrisReader_Bi = BioDevSpecs(
    friendlyName = "Iritech Iris Reader Binocular",
    readingTimeout = 8.seconds,
    deviceKClass = com.verazial.biometry.dev.iritech.IrisReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.IRIS),
    deviceId = "8035:61445",
    deviceName = "IriShield",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 2,
    deviceCapableFormats = listOf(
        IRIS.IMAGE,
        IRIS.ISO_IEC_19794_6_2005
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Identy_FingerprintReader = BioDevSpecs(
    friendlyName = "Identy Fingerprint Reader",
    readingTimeout = 10.seconds,
    deviceKClass = FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "IDENTY_fingerprint_reader",
    deviceName = "IDENTY",
    deviceCanTakeUnknownSamples = false,
    deviceMaxSamples = 4,
    deviceCapableFormats = listOf(
        FINGER.IMAGE,
        FINGER.ISO_IEC_19794_4_2005,
        FINGER.ISO_IEC_19794_2_2005
    ),
    deviceBinder = {
        PreviewViewBinder(
            licenseFileName = "2429-com.verazial.biometry_test-03-08-2023.lic",
            timeout = it.bioDevSpecs.readingTimeout,
            activity = it::activity
        )
    },
    providesPreviews = false,
    providesDistanceStatus = false
)

val Identy_danno_FingerprintReader = BioDevSpecs(
    friendlyName = "laxton 8A",
    readingTimeout = 10.seconds,
    deviceKClass = com.verazial.biometry.dev.ib.FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "4415:5632",
    deviceName = "IB DANNO SCANNER",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE,
    ),
    deviceBinder = null,
    providesPreviews = true,
    providesDistanceStatus = false
)

val Generic_FrontCamera = BioDevSpecs(
    friendlyName = "Generic Front Camera",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.generic.FrontCamera::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FACIAL),
    deviceId = "fic",
    deviceName = "FRONT_INTEGRATED_CAMERA",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.FACE.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = true,
    providesDistanceStatus = false
)

val Generic_BackCamera = BioDevSpecs(
    friendlyName = "Generic Back Camera",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.generic.BackCamera::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FACIAL),
    deviceId = "bic",
    deviceName = "BACK_INTEGRATED_CAMERA",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.FACE.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = true,
    providesDistanceStatus = false
)

val Aratek_BA8200 = BioDevSpecs(
    friendlyName = "Aratek BA8200",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.aratek.FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "CID7000191014000423",
    deviceName = "cid7000",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Aratek_BA8300 = BioDevSpecs(
    friendlyName = "Aratek BA8300",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.aratek.FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "EM1920230613000013",
    deviceName = "em1920",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val IRLINKER_DM6000 = BioDevSpecs(
    friendlyName = "IRLinker DM6000",
    readingTimeout = 15.seconds,
    deviceKClass = com.verazial.biometry.dev.irlinker.DM6000::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.IRIS),
    deviceId = "rk3288",
    deviceName = "C105G",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 2,
    deviceCapableFormats = listOf(
        IRIS.IMAGE,
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val BioMini = BioDevSpecs(
    friendlyName = "SupremaBioMini",
    readingTimeout = 15.seconds,
    deviceKClass = com.verazial.biometry.dev.suprema.BioMini::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "5841:1032",
    deviceName = "Authentication Scanner(V408)",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE,
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Aratek_Marshall = BioDevSpecs(
    friendlyName = "Aratek Marshall",
    readingTimeout = 10.seconds,
    deviceKClass = com.verazial.biometry.dev.ib.FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "4415:4352",
    deviceName = "IB COLUMBO SCANNER",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE,
    ),
    deviceBinder = null,
    providesPreviews = true,
    providesDistanceStatus = false
)
// TEST FOR GM50 device
/*val GM50_FACE = BioDevSpecs(
    friendlyName = "GM50 Face",
    readingTimeout = 15.seconds,
    deviceKClass = Gm50FaceReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FACIAL),
    deviceId = "gm50_face",
    deviceName = "gm50_face",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.FACE.IMAGE
    ),
    deviceBinder = { testContext ->
        val tv = TextureView(testContext.context)
        testContext.activity.runOnUiThread {
            testContext.activity.addContentView(
                tv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        PixsurGm50ViewBinder(activity = testContext.activity, textureView = tv)
    },
    providesPreviews = true,
    providesDistanceStatus = true
)

val GM50_IRIS = BioDevSpecs(
    friendlyName = "GM50 Iris",
    readingTimeout = 15.seconds,
    deviceKClass = Gm50IrisReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.IRIS),
    deviceId = "gm50_iris",
    deviceName = "gm50_iris",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 2,
    deviceCapableFormats = listOf(
        IRIS.IMAGE
    ),
    deviceBinder = { testContext ->
        val tv = TextureView(testContext.context)
        testContext.activity.runOnUiThread {
            testContext.activity.addContentView(
                tv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        PixsurGm50ViewBinder(activity = testContext.activity, textureView = tv)
    },
    providesPreviews = true,
    providesDistanceStatus = true
)*/

val Generic_Iso7816Nfc = BioDevSpecs(
    friendlyName = "Generic ISO7816 NFC",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.generic.NfcDevice::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.NFC),
    deviceId = "iNFC",
    deviceName = "INTEGRATED_NFC",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.NFC.TEXT
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

// To test it, the class Omnikey5022 should be changed to BiometricDevice instead of BiometricDeviceReadWriteBase
val Hid_Omnikey5022 = BioDevSpecs(
    friendlyName = "HID Omnikey 5022",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.hid.Omnikey5022::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.NFC),
    deviceId = "hid5022",
    deviceName = "HID_OMNIKEY_5022",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.NFC.TEXT
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

// To test it, the class ElyctisIdReader should be changed to BiometricDevice instead of BiometricDeviceReadWriteBase
val ElyctisReader = BioDevSpecs(
    friendlyName = "Elyctis",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.elyctis.ElyctisIdReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.NFC),
    deviceId = "Elyctis",
    deviceName = "ElyctisIdReader",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        BiometricSample.Type.NFC.TEXT
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val Aratek_A400M = BioDevSpecs(
    friendlyName = "Aratek A400M Integrado",
    readingTimeout = 5.seconds,
    deviceKClass = com.verazial.biometry.dev.aratek.FingerprintReader::class,
    deviceBiometricTechnologies = setOf(BiometricTechnology.FINGERPRINT),
    deviceId = "00000000050C",
    deviceName = "Aratek Biometric A400M",
    deviceCanTakeUnknownSamples = true,
    deviceMaxSamples = 1,
    deviceCapableFormats = listOf(
        FINGER.IMAGE
    ),
    deviceBinder = null,
    providesPreviews = false,
    providesDistanceStatus = false
)

val allBioDevSpecs: List<BioDevSpecs> = listOf(
    Secugen_U20BLE,
    Pixsur_USBIrisReader,
    //Pixsur_InternalIrisReader,
    Iritech_IrisReader_Mono,
    Iritech_IrisReader_Bi,
    Identy_FingerprintReader,
    Identy_danno_FingerprintReader,
    Generic_FrontCamera,
    Generic_BackCamera,
    Aratek_BA8200,
    Aratek_BA8300,
    IRLINKER_DM6000,
    Aratek_Marshall,
    BioMini,
    //GM50_FACE,
    //GM50_IRIS
    Generic_Iso7816Nfc,
    Hid_Omnikey5022,
    ElyctisReader,
    Aratek_A400M
)