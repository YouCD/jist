package dev.rcht.jist.xposed

import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * Runtime discovery of WeChat's obfuscated image-decode entry points.
 *
 * WeChat renders chat images through a native decoder (libcommonimgdec.so)
 * whose JNI wrapper class is obfuscated and renamed on every release.
 * DexKit locates it without hardcoded names, using two independent patterns:
 *
 *  1. The JNI wrapper class must use the dex string "commonimgdec"
 *     (System.loadLibrary). Every public method of that class taking a
 *     String path is hooked.
 *  2. Any app method with signature (String) -> android.graphics.Bitmap
 *     is a decode-style entry point; all of them are hooked.
 *
 * Hooks only act on paths under /image2/, so false positives elsewhere
 * are harmless. Re-run this on WeChat upgrades; if the patterns stop
 * matching, adjust them here.
 */
object ImageDecodeDiscovery {

    private const val TAG = "JistImageDiscovery"
    private val started = AtomicBoolean(false)
    private val hooked = java.util.concurrent.ConcurrentHashMap<String, Unit>()

    /** Fire-and-forget: discovery runs on its own thread. */
    fun discover(apkPath: String?, loader: ClassLoader) {
        if (apkPath == null || !started.compareAndSet(false, true)) return
        Thread {
            try {
                System.loadLibrary("dexkit")
                val t0 = System.currentTimeMillis()
                DexKitBridge.create(apkPath).use { bridge ->
                    Log.i(TAG, "dex parsed (${bridge.getDexNum()} dex files, ${System.currentTimeMillis() - t0}ms)")
                    // Pattern 1: the libcommonimgdec JNI wrapper.
                    val wrapperMethods = try {
                        bridge.batchFindMethodUsingStrings {
                            addSearchGroup("imgdec", listOf("commonimgdec"), StringMatchType.Contains)
                        }["imgdec"].orEmpty()
                    } catch (e: Exception) {
                        Log.w(TAG, "wrapper search failed: ${e.message}"); emptyList()
                    }
                    val wrapperClasses = wrapperMethods.map { it.className }.toSet()
                    Log.i(TAG, "wrapper classes: $wrapperClasses")
                    for (cd in wrapperClasses.mapNotNull { bridge.getClassData(it) }) {
                        hookWrapperClass(cd, loader)
                    }
                    // Pattern 2: (String) -> Bitmap decode entries.
                    val decodeMethods = try {
                        bridge.findMethod {
                            matcher {
                                paramTypes("java.lang.String")
                                returnType("android.graphics.Bitmap")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "decode search failed: ${e.message}"); emptyList()
                    }
                    for (md in decodeMethods) {
                        val cls = loader.loadClass(md.className)
                        for (m in cls.declaredMethods) {
                            if (m.parameterTypes.size == 1 &&
                                m.parameterTypes[0] === String::class.java &&
                                m.returnType === android.graphics.Bitmap::class.java
                            ) hookMethod(cls, m)
                        }
                    }
                    Log.i(TAG, "discovery done, $hooked method(s) hooked")
                }
            } catch (e: Exception) {
                Log.e(TAG, "discovery failed: ${e.message}", e)
            }
        }.start()
    }

    private fun hookWrapperClass(cd: org.luckypray.dexkit.result.ClassData, loader: ClassLoader) {
        try {
            val cls = loader.loadClass(cd.name)
            for (m in cls.declaredMethods) {
                if (m.parameterTypes.isNotEmpty() &&
                    m.parameterTypes[0] === String::class.java &&
                    java.lang.reflect.Modifier.isPublic(m.modifiers)
                ) hookMethod(cls, m)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wrapper class ${cd.name} not hookable: ${e.message}")
        }
    }

    private fun hookMethod(cls: Class<*>, m: Method) {
        val key = "${cls.name}#${m.name}#${m.parameterTypes.joinToString { it.name }}"
        if (hooked.putIfAbsent(key, Unit) != null) return
        try {
            m.isAccessible = true
            XposedHelpers.findAndHookMethod(cls, m.name,
                *m.parameterTypes,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val path = param.args.getOrNull(0) as? String ?: return
                        if (path.contains("/image2/")) onImageReadDetected(path)
                    }
                })
            Log.i(TAG, "hooked ${cls.simpleName}.${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
        } catch (e: Exception) {
            hooked.remove(key)
            Log.w(TAG, "hook failed ${cls.simpleName}.${m.name}: ${e.message}")
        }
    }

    private fun onImageReadDetected(path: String) {
        Log.i(TAG, "native-decode read detected: $path")
        ImageCapture.onPossibleImageOpen(path)
    }
}
