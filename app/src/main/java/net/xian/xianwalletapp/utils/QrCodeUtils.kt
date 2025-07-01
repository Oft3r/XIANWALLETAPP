package net.xian.xianwalletapp.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtils {

    /**
     * Generates a QR code bitmap for the given content.
     *
     * @param content The string content to encode in the QR code (e.g., wallet address).
     * @param width The desired width of the QR code bitmap in pixels.
     * @param height The desired height of the QR code bitmap in pixels.
     * @return A Bitmap object representing the QR code, or null if generation fails.
     */
    fun generateQrCodeBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap? {
        if (content.isBlank()) {
            return null
        }

        val writer = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, // High error correction
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1 // Keep margin small
        )

        return try {
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            // Log error or handle appropriately
            e.printStackTrace()
            null
        }
    }
}