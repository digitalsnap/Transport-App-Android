package com.ridevibe.feature.checkout.ocr

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifetime of captured discount-ID photos — senior, PWD and student
 * government IDs, which are sensitive personal information under RA 10173.
 *
 * Capture writes them to `cacheDir/discount-ids/` (app-private, excluded from
 * backup, and a directory of its own so a sweep can never touch another
 * feature's cache files). Nothing uploads or displays the file beyond the
 * checkout thumbnail: the server only ever receives the path string and
 * checks that a discount claim carries one. So once the whole checkout has
 * succeeded the photo has no purpose, and this store removes it then — or
 * when checkout is abandoned. A sweep must NOT run between the two legs of a
 * round trip: the return booking is validated against the same files.
 *
 * File I/O runs on [Dispatchers.IO]; [sweepInBackground] exists for
 * `onCleared`, which cannot suspend.
 *
 * When a real ID-upload flow lands (roadmap Phase 3), delete after the upload
 * succeeds instead.
 */
@Singleton
class DiscountIdImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when [path] names one of our photos and it is still on disk. */
    fun exists(path: String?): Boolean {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return false
        return isOurs(file) && file.isFile
    }

    /** Deletes one captured photo without blocking the caller. Ignores paths that aren't ours. */
    fun deleteInBackground(path: String?) {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        if (!isOurs(file)) return
        backgroundScope.launch { file.delete() }
    }

    /** Deletes every captured photo except [keep] — including orphans from an earlier crash. */
    suspend fun sweep(keep: Collection<String> = emptyList()) = withContext(Dispatchers.IO) {
        sweepBlocking(keep)
    }

    /** [sweep] for callers that cannot suspend (`ViewModel.onCleared`). */
    fun sweepInBackground(keep: Collection<String> = emptyList()) {
        backgroundScope.launch { sweepBlocking(keep) }
    }

    private fun sweepBlocking(keep: Collection<String>) {
        val kept = keep.map { File(it).absolutePath }.toSet()
        captureDirectory(context)
            .listFiles { candidate -> candidate.name.startsWith(FILE_PREFIX) }
            ?.filterNot { it.absolutePath in kept }
            ?.forEach { it.delete() }
    }

    private fun isOurs(file: File): Boolean =
        file.name.startsWith(FILE_PREFIX) && file.absoluteFile.parentFile == captureDirectory(context).absoluteFile

    companion object {
        const val FILE_PREFIX = "discount_id_"
        private const val DIRECTORY_NAME = "discount-ids"

        /** The one directory every ID photo lives in; created on demand. */
        fun captureDirectory(context: Context): File =
            File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

        /** A fresh, unique target for the camera or a gallery import. */
        fun newCaptureFile(context: Context): File =
            File(captureDirectory(context), "$FILE_PREFIX${System.currentTimeMillis()}.jpg")

        /**
         * Copies a gallery pick into the capture directory so it follows the
         * same lifetime rules as a camera shot. Null when the picker's stream
         * could not be read (revoked grant, removed file).
         */
        suspend fun importPickedImage(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
            val target = newCaptureFile(context)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                target
            }.getOrElse {
                target.delete()
                null
            }
        }
    }
}
