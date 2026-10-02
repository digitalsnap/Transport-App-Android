package com.ridevibe.feature.ticket.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.feature.ticket.format.TicketFormatter
import com.ridevibe.feature.ticket.ui.QrCodeGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Turns a ticket into a PNG (QR + summary) and moves it out of the app:
 * share sheet via [FileProvider], the Pictures gallery via MediaStore, or a
 * calendar event. Everything that touches disk runs on [Dispatchers.IO]; the
 * caller (the view model) decides what to tell the rider.
 *
 * The PNG is drawn with plain Canvas rather than by snapshotting the Compose
 * card: a snapshot needs an attached view and inherits whatever theme,
 * brightness and scroll state the screen is in, while the export must be a
 * flat, high-contrast, always-scannable image.
 */
class TicketExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val shareAuthority = "${context.packageName}.tickets.fileprovider"

    /** The rendered ticket image. Null only when the QR payload cannot be encoded. */
    suspend fun renderPng(ticket: Ticket, legLabel: String?): Bitmap? = withContext(Dispatchers.Default) {
        val qr = QrCodeGenerator.encode(ticket.qrPayload, sizePx = QR_PX) ?: return@withContext null
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Pure white background: the scanner needs contrast, not the app theme.
        canvas.drawColor(Color.WHITE)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 32f
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 26f
        }

        var y = 80f
        canvas.drawText("RideVibe • ${ticket.trip.operatorName}", MARGIN, y, title)
        y += 44f
        canvas.drawText(
            TicketFormatter.classLabel(ticket) + (legLabel?.let { " • $it" } ?: ""),
            MARGIN,
            y,
            label,
        )

        val qrLeft = (WIDTH - QR_PX) / 2f
        val qrTop = y + 30f
        canvas.drawBitmap(qr, qrLeft, qrTop, null)
        y = qrTop + QR_PX + 60f

        canvas.drawText("TICKET ID", MARGIN, y, label)
        y += 44f
        canvas.drawText(ticket.id, MARGIN, y, title)
        y += 60f

        fun line(heading: String, value: String) {
            canvas.drawText(heading, MARGIN, y, label)
            y += 36f
            canvas.drawText(value, MARGIN, y, body)
            y += 54f
        }
        line("ROUTE", "${ticket.trip.origin} → ${ticket.trip.destination}")
        line(
            "DEPARTURE",
            "${TicketFormatter.dateLabel(ticket.trip.departureEpochMillis)} • " +
                TicketFormatter.timeLabel(ticket.trip.departureEpochMillis),
        )
        line(TicketFormatter.seatNoun(ticket.trip.rideKind, plural = true).uppercase(), ticket.seatLabels.joinToString(", "))
        line("PASSENGER", ticket.primaryPassenger.fullName)
        canvas.drawText(
            "Show this QR to ${TicketFormatter.staffNoun(ticket.trip.rideKind)}.",
            MARGIN,
            HEIGHT - 60f,
            label,
        )
        bitmap
    }

    /** Writes the PNG to `cacheDir/shared-tickets/` and returns a content URI other apps may read. */
    suspend fun writeShareFile(ticket: Ticket, legLabel: String?): Uri? = withContext(Dispatchers.IO) {
        val bitmap = renderPng(ticket, legLabel) ?: return@withContext null
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "${TicketFormatter.fileStem(ticket)}.png")
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, shareAuthority, file)
        }.getOrNull()
    }

    /** ACTION_SEND for one image, ACTION_SEND_MULTIPLE for a round trip; the receiver gets read access. */
    fun shareIntent(uris: List<Uri>, text: String): Intent {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        return intent.apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "RideVibe ticket")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * API 24–28 write to the public Pictures folder directly and need the
     * storage permission; API 29+ goes through MediaStore with none.
     */
    fun needsLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /** Saves the PNG under Pictures/RideVibe. Success carries a short location the Snackbar can show. */
    suspend fun saveToPictures(ticket: Ticket, legLabel: String?): Result<String> = withContext(Dispatchers.IO) {
        val bitmap = renderPng(ticket, legLabel)
            ?: return@withContext Result.failure(IOException("This ticket's QR could not be rendered."))
        val fileName = "${TicketFormatter.fileStem(ticket)}.png"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(bitmap, fileName)
            } else {
                saveLegacy(bitmap, fileName)
            }
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, fileName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$PICTURES_SUBDIRECTORY")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gallery refused the file.")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("Gallery refused the file.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return "Pictures/$PICTURES_SUBDIRECTORY/$fileName"
    }

    private fun saveLegacy(bitmap: Bitmap, fileName: String): String {
        if (needsLegacyStoragePermission()) throw SecurityException("Storage permission is needed to save the ticket.")
        @Suppress("DEPRECATION")
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val directory = File(pictures, PICTURES_SUBDIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Without a scan the gallery app does not show the file until the next reboot.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return "Pictures/$PICTURES_SUBDIRECTORY/$fileName"
    }

    /** Pre-filled calendar event: the trip, from departure to arrival, at the origin terminal. */
    fun calendarIntent(ticket: Ticket): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "RideVibe: ${ticket.trip.origin} → ${ticket.trip.destination}")
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Ticket ${ticket.id} • ${ticket.trip.operatorName} • ${TicketFormatter.seatLabel(ticket)}",
            )
            .putExtra(CalendarContract.Events.EVENT_LOCATION, ticket.trip.origin)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ticket.trip.departureEpochMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ticket.trip.arrivalEpochMillis)

    private companion object {
        const val SHARE_DIRECTORY = "shared-tickets"
        const val PICTURES_SUBDIRECTORY = "RideVibe"
        const val WIDTH = 800
        const val HEIGHT = 1200
        const val QR_PX = 560
        const val MARGIN = 60f
    }
}
