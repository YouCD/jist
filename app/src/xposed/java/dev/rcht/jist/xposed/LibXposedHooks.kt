@file:Suppress("unused")

package dev.rcht.jist.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

// ── libxposed-based hook bridge ─────────────────────────────────────
// Provides a familiar before/after hook API implemented on top of the
// modern libxposed API. No dependency on the legacy de.robv.android.xposed.

abstract class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}

class MethodHookParam {
    var thisObject: Any? = null
    lateinit var args: List<Any?>
    var result: Any? = null
    var throwable: Throwable? = null

    fun getObjectFieldParam(index: Int): Any? = args.getOrNull(index)
}

object XposedHelpers {

    lateinit var module: XposedModule

    private fun resolveClass(name: String, loader: ClassLoader?): Class<*> {
        return if (loader != null) {
            try {
                Class.forName(name, false, loader)
            } catch (_: ClassNotFoundException) {
                Class.forName(name)
            }
        } else Class.forName(name)
    }

    fun findClass(name: String, loader: ClassLoader?): Class<*> = resolveClass(name, loader)

    fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>?): Method? {
        val types = paramTypes.filterNotNull().toTypedArray()
        return try {
            clazz.getDeclaredMethod(name, *types).apply { isAccessible = true }
        } catch (_: Throwable) { null }
    }

    private fun findField(clazz: Any?, name: String): Field? {
        var t: Class<*>? = clazz?.javaClass
        while (t != null) {
            try {
                return t.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                t = t.superclass
            }
        }
        return null
    }

    fun getObjectField(clazz: Any?, name: String): Any? = findField(clazz, name)?.get(clazz)

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        clazz.methods.firstOrNull { it.name == name && it.parameterCount == args.size }?.apply {
            isAccessible = true
            return invoke(null, *args)
        }
        throw NoSuchMethodException("$name in ${clazz.name}")
    }

    fun callMethod(clazz: Any?, name: String, vararg args: Any?): Any? {
        var t: Class<*>? = clazz?.javaClass
        while (t != null) {
            for (m in t.declaredMethods) {
                if (m.name == name && m.parameterCount == args.size) {
                    m.isAccessible = true
                    return m.invoke(clazz, *args)
                }
            }
            t = t.superclass
        }
        throw NoSuchMethodException(name)
    }

    /**
     * Hooks a method. `typesAndHook` consists of zero or more Class arguments
     * describing the method signature, followed by a final XC_MethodHook.
     */
    fun findAndHookMethod(clazz: Class<*>, name: String, vararg typesAndHook: Any?) {
        val params = typesAndHook.dropLast(1).filterIsInstance<Class<*>>().toTypedArray()
        val hook = typesAndHook.lastOrNull() as? XC_MethodHook ?: return
        val method = try {
            clazz.getDeclaredMethod(name, *params).apply { isAccessible = true }
        } catch (_: Throwable) { return }
        if (!XposedHelpers::module.isInitialized) return
        XposedHelpers.module.hook(method).intercept { chain ->
            val param = MethodHookParam()
            param.thisObject = chain.thisObject
            param.args = chain.args
            hook.beforeHookedMethod(param)
            param.result = try {
                chain.proceed()
            } catch (t: Throwable) {
                param.throwable = t
                null
            }
            hook.afterHookedMethod(param)
            param.result
        }
    }
}