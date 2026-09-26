package com.ridevibe.feature.ticket.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the ticket card has for its QR right now. */
sealed interface QrRender {
    data object Loading : QrRender
    data class Ready(val image: ImageBitmap) : QrRender

    /** ZXing refused the payload (empty, or too long for a QR); show the ticket id instead. */
    data object Unavailable : QrRender
}

/**
 * Encodes ticket payloads as boarding-scanner QR bitmaps. Error correction M:
 * the payload is a signed token, so the extra density of level L is not worth
 * losing scans to a scratched screen, and level H would push a long token past
 * what a phone-sized code reads reliably.
 */
object QrCodeGenerator {
    private const val QUIET_ZONE_MODULES = 1

    /** Null when the payload cannot be encoded; callers render a fallback rather than crash. */
    fun encode(payload: String, sizePx: Int = 512): Bitmap? {
        if (payload.isBlank()) return null
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        )
        val bitMatrix = try {
            QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        } catch (e: WriterException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}

/** Encodes off the main thread; the card shows [QrRender.Loading] until it lands. */
@Composable
fun rememberQrCode(payload: String, sizePx: Int = 512): State<QrRender> =
    produceState<QrRender>(initialValue = QrRender.Loading, payload, sizePx) {
        value = withContext(Dispatchers.Default) {
            QrCodeGenerator.encode(payload, sizePx)?.let { QrRender.Ready(it.asImageBitmap()) } ?: QrRender.Unavailable
        }
    }
