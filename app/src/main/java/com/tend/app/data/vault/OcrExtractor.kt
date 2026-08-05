package com.tend.app.data.vault

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads text out of an image so it can be found by Memory search.
 *
 * ## Why this is opt-in
 *
 * Tend otherwise makes no network request you didn't ask for. This uses the
 * **unbundled** ML Kit recognizer, which keeps the model out of the APK and
 * fetches it through Google Play services the first time it runs — so enabling
 * OCR is a decision with a download attached, and the app should not make it on
 * the user's behalf.
 *
 * The recognition itself is fully on-device once the model is present: the image
 * never leaves the phone, which is what makes this safe to run over vault
 * contents at all.
 *
 * On a device without Play services, or before the model has downloaded, this
 * returns null and the entry is simply stored without OCR text. Search still
 * works over captions and filenames, so failure degrades rather than blocks.
 */
object OcrExtractor {

    suspend fun extract(context: Context, uri: Uri): String? = try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(result.text.takeIf { it.isNotBlank() })
                }
                .addOnFailureListener { continuation.resume(null) }
            continuation.invokeOnCancellation { recognizer.close() }
        }
    } catch (e: Exception) {
        // No Play services, an unreadable image, or the model not yet fetched.
        // None of these should stop the file being saved.
        null
    }
}
