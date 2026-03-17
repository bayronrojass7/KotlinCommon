package com.verazial.biometry_test

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import com.verazial.biometry.dev.generic.Iso7816NfcDeviceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NfcProxyActivity : Activity() {

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onResume() {
        super.onResume()

        val device = Iso7816NfcDeviceLocator.get()

        // If no device or no active read → close immediately
        if (device == null || !device.reading) {
            finish()
            return
        }

        if (!enableForegroundDispatch()) {
            finish()
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            Iso7816NfcDeviceLocator.get()?.let { device ->
                activityScope.launch {
                    device.onTagDiscovered(tag)
                    finish()
                }
            }
        }
    }

    private fun enableForegroundDispatch(): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return false

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        val techList = arrayOf(
            arrayOf(android.nfc.tech.IsoDep::class.java.name)
        )

        adapter.enableForegroundDispatch(
            this,
            pendingIntent,
            filters,
            techList
        )

        return true
    }
}
