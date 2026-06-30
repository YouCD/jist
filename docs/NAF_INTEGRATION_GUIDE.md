# NAF 集成指南：AI Agent / 第三方 App 调用通知操作

> 基于 LineageOS Android 16 + Notification Action Framework v2.0

## 一、概述

NAF 提供了一条系统级 API，允许经过授权的 App 以编程方式对通知执行**点击、回复、关闭**等操作。效果等同于用户在通知栏中点击通知。

### 架构

```
授权 App (UID 1000 / platform 签名)
    │  Binder: INotificationManager.performNotificationAction()
    ▼
NotificationManagerService
    │  查找 NotificationRecord（活跃通知 / Shadow Cache）
    ▼
NotificationActionDispatcher
    │  intent.send() 从 system_server 发出
    ▼
目标 App（如 Telegram）的 LaunchActivity
```

### 与传统方案对比

| 方案 | 跨 UID PendingIntent | BAL 后台启动 | 通知上下文完整性 |
|------|---------------------|-------------|----------------|
| `PendingIntent.send()` (第三方 App) | ❌ Android 14+ 拦截 | ❌ | ❌ |
| Framework Patch 掉包 UID | ❌ Binder 驱动层拦截 | ❌ | ⚠️ 部分 |
| **NAF** | ✅ system_server 发送 | ✅ 需 PendingIntentRecord 补丁 | ✅ 完整 extras |
| SystemUI 通知栏点击 | ✅ 系统内部 token | ✅ 用户交互豁免 | ✅ |

---

## 二、Framework 层前提

集成 NA的 ROM 必须包含以下改动（如你的 ROM 已集成则可跳过）：

### 2.1 需要合入的代码

| 文件 | 路径 | 说明 |
|------|------|------|
| `AndroidManifest.xml` | `frameworks/base/core/res/` | 声明 `PERFORM_NOTIFICATION_ACTIONS` 权限 |
| `INotificationManager.aidl` | `frameworks/base/core/java/android/app/` | 添加 `performNotificationAction` AIDL 方法 |
| `NotificationManager.java` | `frameworks/base/core/java/android/app/` | 添加 `@SystemApi` 公开方法 |
| `NotificationActionDispatcher.java` | `frameworks/base/services/core/java/com/android/server/notification/` | **新建**，动作分发器 |
| `NmsCancelRunnable.java` | same as above | **新建**，取消辅助类 |
| `NotificationManagerService.java` | same as above | Binder 实现 + Shadow Cache |
| `strings.xml` | `frameworks/base/core/res/res/values/` | 权限描述字符串 |
| `PendingIntentRecord.java` | `frameworks/base/services/core/java/com/android/server/am/` | BAL 豁免补丁（可选但推荐） |

### 2.2 BAL 豁免补丁（推荐）

如果缺少此补丁，NAF 只能对通知仍处于活跃状态（未过期）时成功跳转。通知发布 30 秒后，BAL 会拦截后台启动。

补丁位置：`PendingIntentRecord.sendInner()`，约第 470 行

```diff
 final int callingUid = Binder.getCallingUid();
 final int callingPid = Binder.getCallingPid();
+final boolean isSystemCaller = callingUid == SYSTEM_UID || callingUid == ROOT_UID;
```

以及 Activity 启动处（约第 641-658 行）：

```diff
+final boolean balAllowed = isSystemCaller
+        || getBackgroundStartPrivilegesForActivitySender(allowlistToken)
+                .allowsBackgroundActivityStarts();
+final PendingIntentRecord originatingPi = isSystemCaller ? null : this;
+
 if (key.allIntents != null && key.allIntents.length > 1) {
     res = controller.mAtmInternal.startActivitiesInPackage(
             uid, callingPid, callingUid, key.packageName, key.featureId,
             allIntents, allResolvedTypes, resultTo, mergedOptions, userId,
             false /* validateIncomingUser */,
-            this /* originatingPendingIntent */,
-            getBackgroundStartPrivilegesForActivitySender(allowlistToken)
-                    .allowsBackgroundActivityStarts());
+            originatingPi, balAllowed);
 } else {
     res = controller.mAtmInternal.startActivityInPackage(uid, callingPid,
             callingUid, key.packageName, key.featureId, finalIntent,
             resolvedType, resultTo, resultWho, requestCode, 0,
             mergedOptions, userId, null, "PendingIntentRecord",
             false /* validateIncomingUser */,
-            this /* originatingPendingIntent */,
-            getBackgroundStartPrivilegesForActivitySender(allowlistToken)
-                    .allowsBackgroundActivityStarts());
+            originatingPi, balAllowed);
 }
```

---

## 三、App 侧集成

### 3.1 前提条件

| 条件 | 说明 | 替代方案 |
|------|------|---------|
| platform 签名 | APK 使用 AOSP 平台密钥签名 | 放入 `/system/priv-app/` + `sharedUserId` |
| `android:sharedUserId="android.uid.system"` | 获取 UID 1000 | 或 ROM 构建时预设 UID |
| `NotificationListenerService` | 捕获通知，获取 notificationKey | — |

### 3.2 AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    android:sharedUserId="android.uid.system">

    <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />

    <application ...>
        <service
            android:name=".NotificationCaptureService"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### 3.3 捕获通知并获取 key

```kotlin
class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val key = sbn.key           // 通知唯一标识，如 "0|org.telegram.messenger|123|null|10083"
        val pkg = sbn.packageName
        val tag = sbn.tag
        val id = sbn.id

        // 存储 key 供后续使用
        notificationStore.save(key, pkg, tag, id, sbn.notification)

        // 可选：保存 contentIntent（用于获取 intent extras 信息）
        val contentIntent = sbn.notification?.contentIntent
        // 注意：contentIntent 在 Android 14+ 无法跨 UID 发送
        // 但可通过 NAF 从 system_server 发送
    }
}
```

### 3.4 调用 NAF 执行通知动作

```kotlin
import android.app.NotificationManager
import android.os.Bundle

/**
 * 通过 NAF 执行通知操作。
 * 需要设备 ROM 集成了 NAF Framework。
 */
fun performNotificationAction(
    context: Context,
    notificationKey: String,
    action: Int,
    extras: Bundle? = null
): Boolean {
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val method = NotificationManager::class.java
            .getDeclaredMethod("performNotificationAction",
                String::class.java, Int::class.java, Bundle::class.java)
        method.invoke(nm, notificationKey, action, extras)
        true
    } catch (e: Exception) {
        Log.w("NAF", "performNotificationAction failed: ${e.message}")
        false
    }
}

// 动作常量（与 Framework 定义一致）
const val ACTION_CONTENT = 1  // 点击通知正文
const val ACTION_DISMISS = 2  // 关闭通知
const val ACTION_REPLY  = 3   // 快速回复（需 RemoteInput）
const val ACTION_MUTE   = 4   // 静音
```

### 3.5 使用示例

```kotlin
// 点击通知（等效于用户点击通知栏）
performNotificationAction(context, "0|org.telegram.messenger|123|null|10083", ACTION_CONTENT)

// 关闭通知
performNotificationAction(context, "0|org.telegram.messenger|123|null|10083", ACTION_DISMISS)

// 快速回复
val results = Bundle().apply {
    putBundle(RemoteInput.EXTRA_RESULTS_DATA, Bundle().apply {
        putString("input", "收到，谢谢！")
    })
}
performNotificationAction(context, "0|com.whatsapp|456|null|10123", ACTION_REPLY, results)
```

---

## 四、完整集成示例（参考 Jist）

### 4.1 通知日志点击

Jist 的 NotificationLogScreen 中，点击通知日志条目的逻辑：

```kotlin
// Priority 1: NAF 执行 contentIntent
var nafSuccess = false
val notifKey = notification.notificationKey
if (!notifKey.isNullOrBlank()) {
    nafSuccess = tryNafAction(ctx, notifKey, 1 /* ACTION_CONTENT */, null)
}

// Priority 2: 回退到 getLaunchIntentForPackage 打开主界面
if (!nafSuccess) {
    val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
    if (intent != null) context.startActivity(intent)
}
```

### 4.2 通知 Key 的获取与存储

在 `NotificationListenerService.onNotificationPosted()` 中：

```kotlin
val notificationKey = sbn.key  // 格式: userId|packageName|notificationId|tag|ownerUid

val entity = NotificationEntity(
    packageName = sbn.packageName,
    notificationId = sbn.id,
    notificationTag = sbn.tag,
    notificationKey = sbn.key,
    // ... 其他字段
)
database.insert(entity)
```

### 4.3 签名与打包

```bash
# 使用 AOSP platform 密钥签名
apksigner sign \
  --key build/security/platform.pk8 \
  --cert build/security/platform.x509.pem \
  --out app-signed.apk \
  app-debug.apk
```

推送到设备：

```bash
adb root && adb remount
adb push app-signed.apk /system/priv-app/YourApp/YourApp.apk
adb reboot
```

---

## 五、支持的 Action 类型

| 常量 | 值 | 说明 | 要求 |
|------|----|------|------|
| `ACTION_CONTENT` | 1 | 发送 contentIntent（跳转到 App 指定页面） | 通知必须有 `contentIntent` |
| `ACTION_DISMISS` | 2 | 取消通知 | — |
| `ACTION_REPLY` | 3 | 发送 RemoteInput 回复 | `extras` 中必须包含 `RemoteInput.EXTRA_RESULTS_DATA` |
| `ACTION_MUTE` | 4 | 静音（暂未实现） | — |

---

## 六、注意事项

### 6.1 通知已消失的情况

通知被清除后，NAF 依赖 **Shadow Cache**（LruCache 容量 50）查找 NotificationRecord。如果缓存已被淘汰，调用会抛出 `IllegalArgumentException`。建议 App 在这种情况下回退到 `getLaunchIntentForPackage()`。

### 6.2 Shadow Cache 生命周期

- 通知从通知栏移除时自动入缓存
- 最多缓存 50 条，FIFO 淘汰
- 缓存的 PendingIntent 只要目标 App 进程存活就有效
- 如果 App 进程被杀或 PendingIntent 被 cancel（ONE_SHOT），缓存中的数据会失效

### 6.3 权限模型

```
Binder 入口 → enforceSystemOrSystemUI()
  ├── callingUid == SYSTEM_UID (1000) → 放行
  ├── callingUid == SystemUI → 放行
  └── 其他 → SecurityException
```

App 必须以 UID 1000 运行（platform 签名 + sharedUserId）或作为 SystemUI 一部分。

### 6.4 动作时效性

- 带有 `FLAG_AUTO_CANCEL` 的通知在 `ACTION_CONTENT` 执行后会自动取消
- 如果希望通知不被自动取消，可以在 NAF Dispatcher 中移除 auto-cancel 逻辑，或在通知创建时不设此 flag
- `ACTION_CONTENT` 执行后通知即进入 Shadow Cache，可再次通过 NAF 触发

---

## 七、常见问题

### Q: NAF 调用成功但目标 App 没跳转到具体对话？

A: 这是目标 App 的内部策略。例如 Telegram 的 `LaunchActivity` 可能校验调用方身份。NAF 已传递完整通知 extras，但部分 App 仅在系统通知栏点击时接受导航。这是**应用层限制**，NAF 框架层无法绕过。

### Q: 不需要修改 PendingIntentRecord 可以工作吗？

A: 可以，但有限制。不修改时，NAF 仅在**通知发布 30 秒内**（BAL 豁免窗口内）有效。超过 30 秒后 BAL 会拦截后台启动。建议合入 BAL 补丁以获得完整的离线触发能力。

### Q: 为什么不用 `PendingIntent.send()` 直接发送？

A: Android 14+ 引入了跨 UID PendingIntent 限制，第三方 App（即使 UID 1000）调用 `PendingIntent.send()` 会被 Binder 驱动层拦截，抛出 `CanceledException`。NAF 从 `system_server` 内部发送，绕过此限制。
