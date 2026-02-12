package dev.rcht.jist.util

/**
 * Canonical list of messaging and email apps
 * Used by both Onboarding and App Settings screens
 */
object MessagingApps {
    
    val MESSAGING_APP_PACKAGES = setOf(
        // WhatsApp
        "com.whatsapp", "com.whatsapp.w4b",
        // Telegram
        "org.telegram.messenger", "org.telegram.plus", "org.telegram.messenger.web",
        // Signal
        "org.thoughtcrime.securesms", "org.whispersystems.signal",
        // Gmail & Google
        "com.google.android.gm", "com.google.android.apps.gmail",
        // Outlook & Microsoft
        "com.microsoft.office.outlook", "com.microsoft.exchange.email",
        // Slack
        "com.Slack", "com.slack",
        // Discord
        "com.discord",
        // Viber
        "com.viber.voip",
        // LINE
        "jp.naver.line.android",
        // WeChat
        "com.tencent.mm",
        // QQ
        "com.tencent.mobileqq",
        // Twitter/X
        "com.twitter.android", "com.x.android",
        // Instagram
        "com.instagram.android",
        // Facebook/Meta
        "com.facebook.orca", "com.facebook.mlite", "com.facebook.katana",
        // iMessage
        "com.apple.mobilesms",
        // Amazon Chime
        "com.amazon.chime",
        // Skype
        "com.skype.raider",
        // Threema
        "ch.threema.app",
        // Wickr
        "com.wickr.pro",
        // ProtonMail
        "com.protonmail.android",
        // Tutanota
        "de.tutao.tutanota",
        // BlueMail
        "me.bluemail.mail",
        // Spark Email
        "com.readdle.spark",
        // Fastmail
        "au.com.fastmail.mail",
        // Spike Email
        "com.mail.spike",
        // LinkedIn
        "com.linkedin.android",
        // Snapchat
        "com.snapchat.android",
        // Kik
        "kik.android",
        // Teams
        "com.microsoft.teams",
        // Zoom
        "us.zoom.videomeetings",
        // SMS/MMS
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        // Chat
        "com.google.android.talk", "com.google.android.apps.tachyon",
        // Wire
        "com.wire",
        // Element/Matrix
        "im.vector.app",
        // Session
        "network.loki.messenger",
        // Yahoo Mail
        "com.yahoo.mobile.client.android.mail",
        // Stock Email
        "com.android.email", "com.samsung.android.email.provider",
        // More email clients
        "com.google.android.apps.gm",
        // WhatsApp Business
        "com.whatsapp.business",
        // Android Messages
        "com.android.messaging",
        // Google Hangouts
        "com.google.android.talk", "com.google.hangouts",
        // More messaging apps
        "com.kakao.talk",
        "com.viber.app",
        "com.jajja.viber",
        "im.gitter.gitter",
        "org.briarproject.briar.android",
        "com.jotapp",
        "org.kontalk",
        "com.kaleyra.app.mobilevideo",
        "org.jami.gnunet",
        "com.nextdoor",
        "com.nextdoor.android"
    )

    fun isMessagingOrEmailApp(packageName: String): Boolean {
        return packageName in MESSAGING_APP_PACKAGES
    }
}
