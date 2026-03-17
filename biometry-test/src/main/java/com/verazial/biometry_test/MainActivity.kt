package com.verazial.biometry_test

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
/*import cn.com.pixsur.devicesdk.OnErrorListener
import cn.com.pixsur.gmfaceiris.GmSDK
import cn.com.pixsur.gmfaceiris.SerialConfig
import cn.com.pixsur.gmfaceiris.sensetime.FaceHandler*/
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.verazial.biometry_test.feat_main.MainScreenH
import com.verazial.biometry_test.feat_main.MainScreenV
import com.verazial.biometry_test.ui.theme.Colors
import com.verazial.biometry_test.ui.theme.KotlinCommonTheme
import com.verazial.core.interfaces.NfcSessionRequester

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not delete, this is used for GM50 devices
        //initializeGm50()

        NfcSessionRequester.instance = object : NfcSessionRequester {
            override fun requestNfcSession() {
                this@MainActivity.requestNfcSession()
            }
        }

        setContent {
            val permissionsState = rememberMultiplePermissionsState(
                buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    add(Manifest.permission.CAMERA)
                }
            )


            KotlinCommonTheme {
                BaseCard {
                    when {
                        !permissionsState.allPermissionsGranted ->
                            LaunchedEffect(permissionsState) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ->
                            MainScreenH(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                activity = this@MainActivity
                            )
                        else ->
                            MainScreenV(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                activity = this@MainActivity
                            )
                    }
                }
            }
        }
    }

    fun requestNfcSession() {
        val intent = Intent(this, NfcProxyActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /*private fun initializeGm50() {
        System.loadLibrary("c++_shared")
        System.loadLibrary("opencv_java4")
        val serialConfig = SerialConfig().apply {
            serialPortBaudRate = 115200
            serialPortDataBit = 8
            serialPortParity = "0"
            serialPortStop = 1
            serialPortRepeatTimes = 3
            serialPortPeriod = 100
        }
        GmSDK.mInstance.setSerialConfig(serialConfig)
        GmSDK.mInstance.initSDK(this, object : OnErrorListener {
            override fun onError(isFatal: Boolean) {
                Log.e("GM50", "Fatal UART error")
            }
        })

        // This is needed for motor movement
        GmSDK.faceHandler.registerFaceSDKInitListener(faceInitListener)
        GmSDK.faceHandler.start( this, "" )
        Log.d("KonektorService", "Face SDK started from service")
    }

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
    }*/
}

@Composable
private fun BaseCard(
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Colors.BackgroundLight,
                    Colors.BackgroundDark,
                )
            )
        ),
    content = content,
)