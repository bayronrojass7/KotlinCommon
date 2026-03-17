package com.verazial.biometry.lib.util

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal val ByteArray.base64String
    get() = Base64.encodeToString(this, Base64.NO_WRAP)

internal fun Context.log(data: String) {
    val path = getExternalFilesDir("/")?.absolutePath + "/log.txt"
    val file = File(path)
    try {
        val stream = FileOutputStream(file, true)
        stream.write(data.toByteArray() + "\n".toByteArray())
        stream.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}