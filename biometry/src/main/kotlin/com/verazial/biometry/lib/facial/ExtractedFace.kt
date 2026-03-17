package com.verazial.biometry.lib.facial

import android.graphics.Rect
import androidx.core.graphics.iterator
import androidx.core.graphics.minus
import androidx.core.util.rangeTo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.verazial.biometry.lib.util.ImageUtil
import com.verazial.biometry.lib.util.ImageUtil.area
import com.verazial.biometry.lib.util.ImageUtil.crop
import com.verazial.biometry.lib.util.ImageUtil.jpegToBitmap
import com.verazial.biometry.lib.util.ImageUtil.rotate
import com.verazial.biometry.lib.util.ImageUtil.toJpeg
import com.verazial.biometry.lib.util.base64String
import com.verazial.core.model.BiometricSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ExtractedFace(
    private val inputImage: InputImage,
    private val face: Face
) : Comparable<ExtractedFace> {
    private companion object {
        const val validEulerVariation = 13f
        const val minHorizontalPixels = 110
        const val maxMovedPercentage = 0.02f
        const val heightMultiplier = 1.2f
        const val widthMultiplier = 1f
        const val croppingHeightMultiplier = 1.6f
        const val croppingWidthMultiplier = 1.2f
    }

    private val image =
        inputImage.mediaImage ?: throw IllegalStateException("Media image can not be null")

    var previousExtractedFace: ExtractedFace? = null
        set(value) {  // Delete previous' previous face, to not form an unnecessary reference chain blocking garbage collection
            field = value?.also { it.previousExtractedFace = null }
        }

    val imageRect: Rect =
        if (inputImage.rotationDegrees == 90 || inputImage.rotationDegrees == 270)
            Rect(0, 0, inputImage.height, inputImage.width)
        else Rect(0, 0, inputImage.width, inputImage.height)

    val faceRect = Rect(face.boundingBox)
        .apply {  // Change box size according to multipliers
            val verticalInset = -((height() * (heightMultiplier - 1)) / 2).roundToInt()
            val horizontalInset = -((width() * (widthMultiplier - 1)) / 2).roundToInt()
            inset(horizontalInset, verticalInset)
        }

    private val croppingRect = Rect(face.boundingBox)
        .apply {  // Change box size according to multipliers
            val verticalInset = -((height() * (croppingHeightMultiplier - 1)) / 2).roundToInt()
            val horizontalInset = -((width() * (croppingWidthMultiplier - 1)) / 2).roundToInt()
            inset(horizontalInset, verticalInset)
        }

    val isValid: Boolean
        get() {
            val lastExtractedFace = previousExtractedFace
                ?: return false  // Must have a previous face to compare movement
            val maxMovedArea = lastExtractedFace.faceRect.area() * maxMovedPercentage
            val movedArea =
                (lastExtractedFace.faceRect - faceRect).iterator().asSequence().sumOf { it.area() }
            val movedSincePrevious = movedArea > maxMovedArea
            if (movedSincePrevious)
                return false  // Face must not have moved too much since last position
            val validEulerRange = (-validEulerVariation) rangeTo validEulerVariation
            if (face.headEulerAngleX !in validEulerRange)
                return false  // Face must not be too rotated in X axis
            if (face.headEulerAngleY !in validEulerRange)
                return false  // Face must not be too rotated in Y axis
            if (face.headEulerAngleZ !in validEulerRange)
                return false  // Face must not be too rotated in Z axis
            if (imageRect.contains(croppingRect).not())
                return false  // CroppedFace must be inside the image
            val faceBigEnough = croppingRect.width() > minHorizontalPixels
            if (faceBigEnough.not())
                return false  // CroppedFace width pixels must be greater than  a minimum

            return true
        }

    /**
     * Create a [BiometricSample] from the extracted face data,
     * must only be called if [isValid] is true.
     *
     * @throws IllegalStateException if [isValid] is false
     */
    suspend fun toBiometricSample(): BiometricSample {
        check(isValid)

        val imageBase64String = withContext(Dispatchers.IO) {
            ImageUtil.yuvImageToJpegByteArray(image)
                .jpegToBitmap()
                .rotate(inputImage.rotationDegrees)
                .crop(croppingRect)
                .toJpeg()
                .base64String
        }


        return BiometricSample(
            contents = imageBase64String,
            type = BiometricSample.Type.FACE,
            subtype = BiometricSample.Type.FACE.FRONTAL_FACE,
            format = BiometricSample.Type.FACE.IMAGE,
            quality = -1
        )
    }

    override fun compareTo(other: ExtractedFace): Int =
        faceRect.area() - other.faceRect.area()
}