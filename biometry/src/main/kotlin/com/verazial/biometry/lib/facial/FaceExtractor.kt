package com.verazial.biometry.lib.facial

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class FaceExtractor {
    private val faceDetectorOptions =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    suspend fun getFacesInImage(image: InputImage): List<ExtractedFace> =
        suspendCancellableCoroutine { continuation ->
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    val extractedFaces =
                        runCatching { faces.map { ExtractedFace(image, it) } }
                            .getOrNull().orEmpty()
                    continuation.resume(extractedFaces)
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
}

