package com.mshomeguardian.logger.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import java.io.File
import java.io.FileOutputStream

/**
 * Utility object that handles capturing of wallpaper and screen contents.
 */
object ScreenContentManager {

    /**
     * Captures the current device wallpaper and writes it to the app's
     * external pictures directory.
     *
     * @return the [File] pointing to the saved wallpaper image
     */
    fun captureWallpaper(context: Context): File {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = wallpaperManager.drawable
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val outputFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "wallpaper.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outputFile
    }

    /**
     * Captures the device screen using the MediaProjection API. This requires
     * that the calling component has already obtained the user's consent and
     * passes the `resultCode` and `data` intent obtained from
     * [MediaProjectionManager.createScreenCaptureIntent].
     *
     * @return the [File] pointing to the saved screenshot or `null` if capture failed
     */
    fun captureScreenshot(
        context: Context,
        resultCode: Int,
        data: Intent
    ): File? {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            0,
            imageReader.surface,
            null,
            null
        )

        var image: Image? = null
        return try {
            image = imageReader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val outputFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshot.png")
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                outputFile
            } else {
                null
            }
        } finally {
            image?.close()
            virtualDisplay.release()
            imageReader.close()
            mediaProjection.stop()
        }
    }
}
