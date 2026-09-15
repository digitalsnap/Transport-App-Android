package com.ridevibe.feature.checkout.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifetime of captured discount-ID photos — senior, PWD and student
 * government IDs, which are sensitive personal information under RA 10173.
 *
 * Capture writes them to cacheDir (app-private, excluded from backup). Nothing
 * uploads or displays the file: the server only ever receives the path string
 * and checks that a discount claim carries one. So once OCR has run the photo
 * has no purpose, and this store removes it at every exit from checkout —
 * booking confirmed, checkout abandoned, or a retake replacing it. [sweep]
 * also clears anything a crash left behind.
 *
 * When a real ID-upload flow lands (roadmap Phase 3), delete after the upload
 * succeeds instead.
 */
@Singleton
class DiscountIdImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Deletes one captured photo. Ignores paths that aren't ours. */
    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.name.startsWith(FILE_PREFIX) && file.parentFile == context.cacheDir) file.delete()
    }

    /** Deletes every captured photo, including orphans from an earlier crash. */
    fun sweep() {
        context.cacheDir
            .listFiles { candidate -> candidate.name.startsWith(FILE_PREFIX) }
            ?.forEach { it.delete() }
    }

    companion object {
        const val FILE_PREFIX = "discount_id_"
    }
}
