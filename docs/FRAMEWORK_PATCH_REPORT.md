# Framework Patch 验证报告：跨进程 PendingIntent 精确跳转

> 自编译 LineageOS Android 16，修改 `PendingIntentRecord.java` 以实现第三方 APP 精确跳转到 Telegram 对话。

---

## 1. 目标

### 1.1 场景

用户通过 Jist（通知摘要 APP）的通知日志，点击一条 Telegram 消息条目（如 `ycd_bot`），期望：
1. 打开 Telegram
2. 自动进入 `ycd_bot` 对应的具体对话

### 1.2 技术障碍

Android 14+ 引入了 Intent Redirection Protection 和跨 UID PendingIntent 限制，导致 `PendingIntent.send()` 在第三方 APP 中调用时抛出 `CanceledException`。

即使绕过此限制，还存在以下问题：
- `FLAG_ONE_SHOT`：PendingIntent 发送一次后即被销毁
- `FLAG_ACTIVITY_NEW_TASK` 缺失：Activity 可能无法正常启动
- 调用方身份校验：目标 APP（Telegram）可能拒绝非系统发起的导航

---

## 2. 环境

| 项目 | 值 |
|------|-----|
| 设备 | 自编译 LineageOS Android 16 |
| 目标 APP | AyuGram (org.telegram.messenger) |
| 调用方 | Jist (dev.rcht.jist) |
| 权限 | Root + KernelSU + JingMatrix(LSPosed) |
| 修改文件 | `frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java` |

---

## 3. 核心代码修改

### 3.1 修改点总览

`PendingIntentRecord.java` 的 `sendInner()` 方法共三处修改：

| # | 位置 | 目的 |
|---|------|------|
| 1 | `sendInner()` 入口 | 识别 Jist 调用 + Dump Intent 数据 |
| 2 | ONE_SHOT 检查处 | 豁免 Jist，使 PendingIntent 可重复使用 |
| 3 | finalIntent 构建处 | 追加 `FLAG_ACTIVITY_NEW_TASK` |
| 4 | startActivityInPackage 调用处 | 掉包 callingUid 为 SYSTEM_UID |

### 3.2 Patch 代码

```java
// ==========================================
// 修改 1：sendInner() 入口 — 识别 + Dump
// ==========================================
final int callingUid = Binder.getCallingUid();
final int callingPid = Binder.getCallingPid();

boolean isJist = false;
if (key != null) {
    Slog.i("JistDebug", "sendInner: callingUid=" + callingUid + " ownerUid=" + uid
            + " flags=0x" + Integer.toHexString(key.flags)
            + " isOS=" + ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0));
    try {
        String[] pkgs = android.app.AppGlobals.getPackageManager()
                .getPackagesForUid(callingUid);
        String pkg = (pkgs != null && pkgs.length > 0) ? pkgs[0] : null;
        Slog.i("JistDebug", "  callerPkg=" + pkg);
        isJist = "dev.rcht.jist".equals(pkg) || "dev.rcht.jist.hook".equals(pkg);
    } catch (Exception e) {
        Slog.w("JistDebug", "  pkg lookup failed: " + e.getMessage());
    }
}
if (key != null && key.requestIntent != null) {
    Slog.i("JistDebug", "=== Jist PendingIntent.send() ===");
    Slog.i("JistDebug", "callingUid=" + callingUid + " ownerUid=" + uid);
    Slog.i("JistDebug", "flags=0x" + Integer.toHexString(key.flags));
    Slog.i("JistDebug", "isONE_SHOT=" + ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0));
    Slog.i("JistDebug", "requestIntent.action=" + key.requestIntent.getAction());
    Slog.i("JistDebug", "requestIntent.data=" + key.requestIntent.getData());
    Slog.i("JistDebug", "requestIntent.component=" + key.requestIntent.getComponent());
    Bundle ex = key.requestIntent.getExtras();
    if (ex != null && !ex.isEmpty()) {
        for (String k : ex.keySet()) {
            Object v = ex.get(k);
            Slog.i("JistDebug", "  extra " + k + "=" + v
                    + " (" + (v != null ? v.getClass().getSimpleName() : "null") + ")");
        }
    } else {
        Slog.i("JistDebug", "  Intent has NO extras");
    }
}

// ==========================================
// 修改 2：ONE_SHOT 豁免
// ==========================================
// 在 synchronized 块内，sent = true 之后：
sent = true;
if ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0 && !isJist) {
    controller.cancelIntentSender(this, true, CANCEL_REASON_ONE_SHOT_SENT);
}

// ==========================================
// 修改 3：追加 FLAG_ACTIVITY_NEW_TASK
// ==========================================
finalIntent = key.requestIntent != null ? new Intent(key.requestIntent) : new Intent();
if (isJist) {
    finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
}

// ==========================================
// 修改 4：掉包 callingUid 为 SYSTEM_UID
// ==========================================
// 在 startActivitiesInPackage 和 startActivityInPackage 调用处：
final int spoofedUid = isJist ? 1000 : callingUid;
if (isJist) Slog.i("JistDebug", "  spoofedUid=" + spoofedUid);
res = controller.mAtmInternal.startActivityInPackage(uid, callingPid,
        spoofedUid, key.packageName, key.featureId, finalIntent,
        ...);
```

---

## 4. 测试过程

### 4.1 测试流程

```
1. 修改 PendingIntentRecord.java → 加入 Patch
2. make bootimage → 编译 boot.img
3. fastboot flash boot boot.img → 刷入设备
4. 重启设备
5. 在 Jist 中点击 ycd_bot 通知日志条目
6. adb logcat -s JistDebug 查看日志
7. 观察 Telegram 是否跳转到 ycd_bot 对话
```

### 4.2 历次测试结果

#### 测试 1：基础 Patch（ONE_SHOT + NEW_TASK）

| 项目 | 结果 |
|------|------|
| ONE_SHOT 移除 | ✅ 成功，PI 可重复发送 |
| NEW_TASK 追加 | ✅ 成功，无崩溃 |
| Dump 输出 | ✅ 成功 |

**关键 Dump 输出**：
```
sendInner: callingUid=10193 ownerUid=10083 flags=0x44000000 isOS=true
  callerPkg=dev.rcht.jist
  requestIntent.action=com.tmessages.openchat0.0151767684308927732147483647
  requestIntent.component=ComponentInfo{org.telegram.messenger/org.telegram.ui.LaunchActivity}
  extra userId=8791525006 (Long)         ← 对话 ID
  extra currentAccount=0 (Integer)
```

**结论**：Intent 中包含 `userId=8791525006`（对话 ID），但 Telegram 不导航。

#### 测试 2：移除 FLAG_ACTIVITY_CLEAR_TOP

去掉 `CLEAR_TOP` flag，只保留 `NEW_TASK`，排除任务栈干扰。

| 项目 | 结果 |
|------|------|
| 去掉 CLEAR_TOP | ✅ 成功 |
| 跳转到具体对话 | ❌ 仍然失败 |

#### 测试 3：掉包 callingUid = 1000 (SYSTEM_UID)

将启动 Activity 时的 `callingUid` 从 Jist 的 UID 改为 SYSTEM_UID，伪装成系统调用。

| 项目 | 结果 |
|------|------|
| `spoofedUid=1000` | ✅ 日志确认掉包成功 |
| 路径 | ✅ `single intent path`（非 allIntents 路径） |
| 跳转到具体对话 | ❌ 仍然失败 |

**Dump 输出**：
```
single intent path, spoofedUid=1000
extra userId=8791525006 (Long)
```

### 4.3 其他已验证方案

| 方案 | 结果 |
|------|------|
| `PendingIntent.send()` 原生调用 | ❌ Android 14+ 跨 UID 拦截 |
| 系统 UID + priv-app + platform 签名 | ❌ 仍抛 `CanceledException` |
| 深度链接 `https://t.me/xxx` | ❌ 需要用户名，通知只有显示名 |
| MessagingStyle `Person.uri/key` | ❌ 均为 null |
| LSPosed Hook `PendingIntent.send()` | ⚠️ 绕过 UID，ONE_SHOT 限制 |
| LSPosed 注入 Telegram + 广播导航 | ❌ AyuGram SIGSEGV 崩溃 |

---

## 5. 结论

### 5.1 Framework Patch 有效性

| 修复项 | 状态 | 验证方式 |
|--------|------|---------|
| 跨 UID PendingIntent 发送 | ✅ 成功 | `sendInner()` 正常执行 |
| FLAG_ONE_SHOT 移除 | ✅ 成功 | 同一 PI 可连续发送多次 |
| FLAG_ACTIVITY_NEW_TASK 追加 | ✅ 成功 | Activity 正常启动 |
| callingUid 掉包为 SYSTEM_UID | ✅ 成功 | `spoofedUid=1000` |
| Intent 数据传输 | ✅ 成功 | `userId=8791525006` 正确传递 |
| Telegram 精确导航 | ❌ **失败** | Telegram 不响应导航指令 |

### 5.2 根因分析

Framework 侧已做到极限。Telegram 仍不导航的原因推断：

1. **Telegram 不使用 `userId` extra 导航** → `LaunchActivity` 可能通过其他路径加载对话
2. **Telegram 使用内部 Binder token 校验调用方** → `getOriginatingPendingIntent()` 或其他系统服务 API
3. **Telegram 的 Action 字符串包含路由信息** → `com.tmessages.openchat...` 可能通过内部广播处理

### 5.3 最终结论

**在 Android 16 的 AyuGram 环境下，Framework 层修改无法使第三方 APP 精确跳转到 Telegram 具体对话。**

这是一个应用层（Telegram）的内部策略限制，Framework 层的手段（UID 掉包、FLAG 修改、Intent 注入）均无法突破。这是 Android 生态中第三方 APP 与系统通知交互的根本性限制。

---

## 附录 A：关键代码清单

### A.1 `PendingIntentRecord.java` — Framework 层核心 Patch

**路径**：`frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java`

**方法**：`sendInner(IApplicationThread caller, int code, Intent intent, ...)`

```java
public int sendInner(IApplicationThread caller, int code, Intent intent,
        String resolvedType, IBinder allowlistToken, IIntentReceiver finishedReceiver,
        String requiredPermission, IBinder resultTo, String resultWho, int requestCode,
        int flagsMask, int flagsValues, Bundle options) {
    final int callingUid = Binder.getCallingUid();
    final int callingPid = Binder.getCallingPid();

    // ===== 修改 1：识别 Jist 调用 + Dump =====
    boolean isJist = false;
    if (key != null) {
        Slog.i("JistDebug", "sendInner: callingUid=" + callingUid + " ownerUid=" + uid
                + " flags=0x" + Integer.toHexString(key.flags)
                + " isOS=" + ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0));
        try {
            String[] pkgs = android.app.AppGlobals.getPackageManager()
                    .getPackagesForUid(callingUid);
            String pkg = (pkgs != null && pkgs.length > 0) ? pkgs[0] : null;
            Slog.i("JistDebug", "  callerPkg=" + pkg);
            isJist = "dev.rcht.jist".equals(pkg) || "dev.rcht.jist.hook".equals(pkg);
        } catch (Exception e) { }
    }
    if (key != null && key.requestIntent != null) {
        Slog.i("JistDebug", "=== Jist PendingIntent.send() ===");
        Slog.i("JistDebug", "callingUid=" + callingUid + " ownerUid=" + uid);
        Slog.i("JistDebug", "flags=0x" + Integer.toHexString(key.flags));
        Slog.i("JistDebug", "isONE_SHOT=" + ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0));
        Bundle ex = key.requestIntent.getExtras();
        if (ex != null) {
            for (String k : ex.keySet()) {
                Slog.i("JistDebug", "  extra " + k + "=" + ex.get(k));
            }
        } else {
            Slog.i("JistDebug", "  Intent has NO extras");
        }
    }

    // ... (中间逻辑不变) ...

    synchronized (controller.mLock) {
        // ... canceled 检查 ...

        sent = true;
        // ===== 修改 2：ONE_SHOT 豁免 Jist =====
        if ((key.flags & PendingIntent.FLAG_ONE_SHOT) != 0 && !isJist) {
            controller.cancelIntentSender(this, true, CANCEL_REASON_ONE_SHOT_SENT);
        }

        finalIntent = key.requestIntent != null ? new Intent(key.requestIntent) : new Intent();
        // ===== 修改 3：追加 NEW_TASK（finalIntent 已是 clone） =====
        if (isJist) {
            finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        // ... immutable 检查、options 处理 ...

        // ===== 修改 4：Activity 启动处掉包 callingUid =====
        if (key.allIntents != null && key.allIntents.length > 1) {
            final int spoofedUid = isJist ? 1000 : callingUid;            // ← 掉包
            res = controller.mAtmInternal.startActivitiesInPackage(
                    uid, callingPid, spoofedUid, key.packageName, ...);
        } else {
            final int spoofedUid = isJist ? 1000 : callingUid;            // ← 掉包
            res = controller.mAtmInternal.startActivityInPackage(
                    uid, callingPid, spoofedUid, key.packageName, ...);
        }
    }
}
```

---

### A.2 `NotificationLogScreen.kt` — Jist 点击跳转逻辑

**路径**：`app/src/main/java/dev/rcht/jist/ui/screens/NotificationLogScreen.kt`

```kotlin
// 在 Card.clickable 的 else 分支中：
} else {
    // 对 Telegram 发送 GOTO_CHAT 广播（供 LSPosed 模块使用）
    if (notification.packageName == "org.telegram.messenger") {
        ctx.sendBroadcast(Intent("dev.rcht.jist.GOTO_CHAT").apply {
            putExtra("title", notification.title)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }
    // Priority 1: PendingIntent（Framework Patch 已处理 ONE_SHOT + NEW_TASK + UID 掉包）
    var jumped = false
    val pi = try {
        notification.pendingIntentData
            ?.let { PendingIntentStore.fromBase64(it) }
            ?: PendingIntentStore.get(notification.conversationKey)
    } catch (e: Exception) { null }
    if (pi != null) {
        try {
            pi.send(ctx, 0, null)
            jumped = true
        } catch (_: PendingIntent.CanceledException) {
            PendingIntentStore.remove(notification.conversationKey)
        }
    }
    // Priority 2: ChatIntentBuilder fallback
    if (!jumped) {
        val intent = ChatIntentBuilder.buildChatIntent(
            ctx, notification.packageName,
            notification.conversationKey, notification.title
        )
        if (intent != null) ctx.startActivity(intent)
    }
}
```

---

### A.3 `ChatIntentBuilder.kt` — Telegram 打开主界面

**路径**：`app/src/main/java/dev/rcht/jist/util/ChatIntentBuilder.kt`

```kotlin
object ChatIntentBuilder {

    fun buildChatIntent(
        context: Context?, packageName: String,
        conversationKey: String, contactOrGroup: String
    ): Intent? {
        if (context == null) return null
        return when (packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> buildWhatsAppIntent(context)
            "org.telegram.messenger" -> buildTelegramIntent(context)
            "com.google.android.gm" -> buildGmailIntent(context)
            else -> buildGenericAppIntent(context, packageName)
        }
    }

    private fun buildTelegramIntent(context: Context): Intent? {
        // 不再尝试 https://t.me/xxx 或 tg://resolve?domain=xxx
        // 因通知仅提供显示名，无法构建可靠深度链接
        return context.packageManager
            .getLaunchIntentForPackage("org.telegram.messenger")?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}
```

---

### A.4 `PendingIntentStore.kt` — PendingIntent 内存缓存

**路径**：`app/src/main/java/dev/rcht/jist/notification/PendingIntentStore.kt`

```kotlin
object PendingIntentStore {
    private val store = mutableMapOf<String, PendingIntent>()
    private val chatUriStore = mutableMapOf<String, String>()

    fun put(key: String, pi: PendingIntent) { store[key] = pi }
    fun get(key: String): PendingIntent? = store[key]
    fun remove(key: String) { store.remove(key); chatUriStore.remove(key) }

    fun putChatUri(key: String, uri: String) { chatUriStore[key] = uri }
    fun getChatUri(key: String): String? = chatUriStore[key]

    // 序列化/反序列化 PendingIntent 到 Base64（用于数据库持久化）
    fun fromBase64(b64: String): PendingIntent? {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return PendingIntent.CREATOR.createFromParcel(parcel)
    }

    fun toBase64(pi: PendingIntent): String? {
        val parcel = Parcel.obtain()
        pi.writeToParcel(parcel, 0)
        return android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.NO_WRAP)
    }
}
```

---

### A.5 `JistNotificationListenerService.kt` — 捕获 PendingIntent

**路径**：`app/src/main/java/dev/rcht/jist/service/JistNotificationListenerService.kt`

```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification?) {
    // ... (数据提取) ...

    val notificationObj = sbn.notification
    val originalPendingIntent = notificationObj?.contentIntent

    // 存入内存缓存（供点击时使用）
    if (originalPendingIntent != null) {
        PendingIntentStore.put(conversationKey, originalPendingIntent)
    }

    // 提取 MessagingStyle Person URI（Telegram 未设置）
    try {
        val messages = notificationObj?.extras
            ?.getParcelableArray("android.messages")
        if (messages != null) {
            for (msg in messages) {
                val person = (msg as? Bundle)
                    ?.getParcelable("sender_person", Person::class.java)
                if (!person?.uri.isNullOrBlank()) {
                    PendingIntentStore.putChatUri(conversationKey, person!!.uri!!)
                    break
                }
            }
        }
    } catch (e: Exception) { }

    // 序列化到数据库（Android 14+ 因含 FD 引用一直失败）
    val piData = originalPendingIntent?.let { pi ->
        try { PendingIntentStore.toBase64(pi) } catch (e: Exception) { null }
    }

    // ... (写入数据库) ...
}
```

---

### A.6 `HookEntry.kt` — LSPosed 测试模块（未成功）

**路径**：`/tmp/jist-hook-test/app/src/main/java/dev/rcht/jist/hook/HookEntry.kt`

```kotlin
class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "org.telegram.messenger" -> hookTelegram(lpparam)
            "dev.rcht.jist" -> hookJist(lpparam)       // Hook PI.send() 看堆栈
            "android" -> hookSystemServer(lpparam)     // Hook PendingIntentRecord
        }
    }

    private fun hookTelegram(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 注册 GOTO_CHAT 广播接收器
        // 按 title 搜索 dialogsArray → 打开 ChatActivity
        // 失败原因：AyuGram PluginsController.applyBlacklist SIGSEGV
    }

    private fun hookJist(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook PendingIntent.send() → 拦截 CanceledException
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook PendingIntentRecord.sendInner() → 绕过 UID 校验
    }
}
```

## 附录 B：Patch 文件位置

修改后的 `PendingIntentRecord.java` 位于：
```
/home/ycd/self_data/source_code/jist/files/PendingIntentRecord.java
```

需将该文件复制到 LineageOS 源码树对应路径：
```
frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java
```

编译命令：
```bash
source build/envsetup.sh
lunch lineage_<device>-userdebug
make bootimage
fastboot flash boot out/target/product/<device>/boot.img
```
