package dev.rcht.jist.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/**
 * Utility for working with drawables
 */
object DrawableUtil {

    /**
     * Convert a Drawable to a Bitmap
     * Used for notification large icons
     */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        // Use recommended size for notification large icons: 256x256 dp
        val width = 256
        val height = 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        return bitmap
    }
}
