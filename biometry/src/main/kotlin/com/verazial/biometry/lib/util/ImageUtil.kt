package com.verazial.biometry.lib.util

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream


internal object ImageUtil {

    fun yuvImageToJpegByteArray(
        image: Image
    ): ByteArray {
        require(image.format == ImageFormat.YUV_420_888) { "Incorrect image format of the input image proxy: " + image.format }

        return NV21toJPEG(YUV420toNV21(image), image.width, image.height)
    }

    fun ByteArray.jpegToBitmap(): Bitmap =
        BitmapFactory.decodeByteArray(this, 0, size, null)

    fun Bitmap.rotate(degrees: Number): Bitmap =
        Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true
        )

    /*fun Bitmap.mirrorH(): Bitmap =
        Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { preScale(-1.0f, 1.0f) },
            true
        )*/

    fun Bitmap.crop(rect: Rect): Bitmap =
        Bitmap.createBitmap(
            this,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )

    fun Bitmap.toJpeg(): ByteArray =
        ByteArrayOutputStream().apply {
            compress(Bitmap.CompressFormat.JPEG, 100, this)
        }.toByteArray().also { recycle() }

    fun Rect.area(): Int =
        width() * height()


    @Suppress("FunctionName")
    private fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int, quality: Int = 100): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    @Suppress("FunctionName")
    private fun YUV420toNV21(image: Image): ByteArray {
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer[data, channelOffset, length]
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }
}