package com.ridevibe.feature.admin.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a booking's `qrPayload` as a QR bitmap for the support console —
 * same ZXing pattern as feature-ticket's QrCodeGenerator, sized for a sheet.
 */
@Composable
fun rememberStaffQrBitmap(payload: String, sizePx: Int = 320): ImageBitmap {
    val bitmap = remember(payload, sizePx) { encodeQrBitmap(payload, sizePx) }
    return remember(bitmap) { bitmap.asImageBitmap() }
}

private fun encodeQrBitmap(payload: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
    )
    val bitMatrix = QRCodeWriter().encode(payload.ifBlank { " " }, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val rowOffset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[rowOffset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
}
