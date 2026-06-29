package dev.rcht.jist.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

/**
 * Builds app-specific intents to open a conversation/chat
 */
object ChatIntentBuilder {

    /**
     * Build an intent to open a specific conversation in an app
     * Returns null if the app is not supported or intent cannot be built
     */
    fun buildChatIntent(
        context: Context?,
        packageName: String,
        conversationKey: String,
        contactOrGroup: String
    ): Intent? {
        if (context == null) return null
        
        return when (packageName) {
            "com.whatsapp" -> buildWhatsAppIntent(context, contactOrGroup)
            "com.whatsapp.w4b" -> buildWhatsAppIntent(context, contactOrGroup) // Business
            "org.telegram.messenger" -> buildTelegramIntent(context, contactOrGroup)
            "com.google.android.gm" -> buildGmailIntent(context, contactOrGroup)
            else -> buildGenericAppIntent(context, packageName)
        }
    }

    /**
     * WhatsApp: Open app with contact/group name
     * Note: WhatsApp doesn't support deep linking directly to chats by name,
     * so we just open the app in general. User will see the conversation in list.
     */
    private fun buildWhatsAppIntent(context: Context, contactOrGroup: String): Intent? {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            launchIntent?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                Log.d(TAG, "WhatsApp launch intent created for: $contactOrGroup")
            } ?: run {
                Log.w(TAG, "WhatsApp launch intent not found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating WhatsApp intent: ${e.message}")
            null
        }
    }

    /**
     * Telegram: Open app to search for chat
     * Telegram supports URI scheme for opening chats by username or contact
     */
    private fun buildTelegramIntent(context: Context, contactOrGroup: String): Intent? {
        return try {
            val encoded = URLEncoder.encode(contactOrGroup, "UTF-8").replace("+", "%20")
            val uri = "tg://resolve?domain=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            Log.d(TAG, "Trying Telegram deep link: $uri")
            intent
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Telegram intent: ${e.message}")
            null
        }
    }

    /**
     * Gmail: Open app to search for sender/conversation
     */
    private fun buildGmailIntent(context: Context, sender: String): Intent? {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            launchIntent?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                Log.d(TAG, "Gmail launch intent created for: $sender")
            } ?: run {
                Log.w(TAG, "Gmail launch intent not found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Gmail intent: ${e.message}")
            null
        }
    }

    /**
     * Generic app opener: just launch the app
     */
    private fun buildGenericAppIntent(context: Context, packageName: String): Intent? {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                Log.d(TAG, "Launch intent created for package: $packageName")
            } ?: run {
                Log.w(TAG, "Launch intent not found for package: $packageName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating launch intent for $packageName: ${e.message}")
            null
        }
    }


    private const val TAG = "ChatIntentBuilder"
}
