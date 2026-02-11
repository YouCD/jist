package dev.rcht.jist.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Utility to extract app icons from other applications
 */
object AppIconExtractor {

    /**
     * Get app icon for a given package name
     * Returns null if app not found or icon unavailable
     */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            val packageManager = context.packageManager
            val icon = packageManager.getApplicationIcon(packageName)
            Log.d(TAG, "✓ App icon extracted for package: $packageName")
            icon
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "✗ App not found for package: $packageName")
            null
        } catch (e: Exception) {
            Log.w(TAG, "✗ Error getting app icon for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Get app label/name for a given package name
     * Returns "Unknown" if app not found
     */
    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App not found for package: $packageName")
            "Unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Error getting app label for $packageName: ${e.message}")
            "Unknown"
        }
    }

    private const val TAG = "AppIconExtractor"
}
