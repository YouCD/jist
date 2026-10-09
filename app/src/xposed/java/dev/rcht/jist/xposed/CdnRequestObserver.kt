package dev.rcht.jist.xposed

import android.util.Log

/**
 * Caches WeChat's own CdnManager instance (seen on the first real C2C
 * download) so ProactiveDownloader can replay downloads without the user
 * tapping an image.
 */
object CdnRequestObserver {

    private const val TAG = "JistCdnObserver"
    private var installed = false

    fun install(loader: ClassLoader) {
        if (installed) return
        try {
            val cdn = XposedHelpers.findClass("com.tencent.mars.cdn.CdnManager", loader)
            XposedHelpers.findAndHookMethod(cdn, "OnJniStartC2CDownload",
                XposedHelpers.findClass("com.tencent.mars.cdn.CdnManager\$C2CDownloadRequest", loader),
                XposedHelpers.findClass("com.tencent.mars.cdn.CdnManager\$DownloadCallback", loader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Any)?.let { ProactiveDownloader.cacheManager(it) }
                    }
                })
            installed = true
            Log.i(TAG, "OnJniStartC2CDownload observer installed")
        } catch (e: Exception) {
            Log.w(TAG, "install failed: ${e.message}")
        }
    }
}
