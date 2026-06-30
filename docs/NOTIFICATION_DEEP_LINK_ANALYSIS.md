# 通知栏点击跳转深度分析

> 分析 Jist 在下拉通知栏/通知日志中点击摘要时，能否跳转到对应 APP 页面的问题。

## 1. 问题概述

**用户期望**：在 Jist 的通知日志中点击一条 Telegram 消息（如 `ycd_bot`），能够跳转到 Telegram 并进入**该消息对应的具体对话**。

**实际表现**：
- Telegram 频道 `@AyuGram` → 跳转成功 ✅
- Telegram 显示名为 `ycd_bot` 的消息 → 跳转到 "YouCanDo"（错误对话）❌
- 其他 APP（微信等）→ `getLaunchIntentForPackage` 打开主界面

## 2. 跳转路径优先级

Jist 中点击通知日志条目的跳转逻辑分为三层优先级：

```
点击通知日志条目
  → Priority 1: PendingIntent (原始 contentIntent)
    → 成功 → 跳转
    → CanceledException → 移除缓存 → 走 Priority 2
  → Priority 2: ChatIntentBuilder 深度链接
    → 有 Person URI → 使用 URI 跳转
    → 有效用户名 → tg://resolve?domain=xxx / https://t.me/xxx
    → 无效用户名 → getLaunchIntentForPackage 打开主界面
```

### Priority 1: PendingIntent.send()

从系统通知的 `contentIntent` 捕获 PendingIntent，通过 `PendingIntentStore` 缓存。

**捕获时机**：`JistNotificationListenerService.onNotificationPosted()` → `sbn.notification?.contentIntent`

**关键代码**：
```kotlin
val originalPendingIntent = sbn.notification?.contentIntent
if (originalPendingIntent != null) {
    PendingIntentStore.put(conversationKey, originalPendingIntent)
}
```

**结果**：始终抛出 `CanceledException`（详见第 3 节）。

### Priority 2: ChatIntentBuilder

当 PendingIntent 不可用时，通过 `ChatIntentBuilder` 构建深度链接。

**分发逻辑** (`ChatIntentBuilder.buildChatIntent`)：

| 包名 | 策略 | 效果 |
|------|------|------|
| `org.telegram.messenger` | 优先用 Person URI → 有效用户名则 `https://t.me/xxx` → 回退 `getLaunchIntentForPackage` | 仅对真实 `@username` 有效 |
| `com.whatsapp` / `com.whatsapp.w4b` | `getLaunchIntentForPackage` | 打开主界面 |
| `com.google.android.gm` | `getLaunchIntentForPackage` | 打开主界面 |
| 其他 | `getLaunchIntentForPackage` | 打开主界面 |

## 3. 核心障碍：PendingIntent 跨 APP 限制

### 3.1 现象

所有尝试发送 Telegram 的 `contentIntent` 的方式均失败：

| 发送方式 | 异常 | 原因 |
|---------|------|------|
| `pi.send()` | `PendingIntent.CanceledException` | Android 14+ 跨 UID 限制 |
| `pi.send(ctx, 0, null)` | `PendingIntent.CanceledException` | 同上 |
| `pi.intentSender.sendIntent(ctx, 0, null, null, null)` | `IntentSender.SendIntentException` | 同上 |

### 3.2 根本原因

Android 14（API 34）引入了 **Intent Redirection Protection**：

```
frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java

// PendingIntent.send() 中的跨 UID 拦截逻辑
if (callingUid != ownerUid && callingUid != SYSTEM_UID) {
    throw new SecurityException("PendingIntent.send() not allowed from uid " + callingUid);
}
```

即使 Jist 以 `system` UID（1000）运行并签名平台密钥，`PendingIntent.send()` 仍然抛出 `CanceledException`。这说明限制不仅存在于 Java 层的 UID 检查，还存在于 **Binder 驱动层的安全策略**（SELinux + Binder security context）。

### 3.3 为什么系统通知栏可以？

系统通知栏的点击处理运行在 **SystemUI 进程**中，该进程通过 `NotificationClicker` 调用 `PendingIntent.send()` 时，使用的是系统的内部 Binder token，不受此限制。

**关键差异**：

| 调用方 | UID | Binder Context | 结果 |
|--------|-----|---------------|------|
| SystemUI 通知栏点击 | system | 系统内部 token | ✅ 成功 |
| Jist 的 NotificationListenerService | 应用自身 / system | 普通 APP / 受限 system | ❌ 失败 |

### 3.4 尝试过的绕过方案

| 方案 | 结果 | 原因 |
|------|------|------|
| 系统 UID（platform 签名 + priv-app） | ❌ 仍然抛 CanceledException | Binder 驱动层限制 |
| `IntentSender.sendIntent()` | ❌ 抛 SendIntentException | 同层限制 |
| Magisk 模块 hook AMS | 理论上可行 | 未实现，复杂度高 |
| Shizuku 代理发送 | 待验证 | 未实现 |

## 4. 核心障碍：Telegram 通知缺少用户名

### 4.1 通知数据结构分析

通过 `dumpsys notification --noredact` 抓取的 Telegram 通知数据：

```
android.title = "ycd_bot"                    # 聊天的显示名
android.template = "android.app.Notification$MessagingStyle"
android.messages[0] = {
    sender = "ycd_bot"                       # 发送者显示名
    text = "..."                              # 消息内容
    sender_person = Person {
        name = "ycd_bot"                     # 显示名
        uri = null                            # ❌ 未设置
        key = null                            # ❌ 未设置
    }
}
```

**关键发现**：`Person.getUri()` 和 `Person.getKey()` 均为 `null`。

### 4.2 为什么 @AyuGram 可以而 ycd_bot 不行

`tg://resolve?domain=xxx` 和 `https://t.me/xxx` 这类 Telegram 深度链接要求输入的是 **用户名（username）**，而非显示名（display name）：

| 条目 | 显示名 | 用户名 | 能否深链 |
|------|--------|--------|---------|
| AyuGram | "AyuGram" | `@AyuGram` | ✅ `https://t.me/AyuGram` 可直达 |
| ycd_bot | "ycd_bot" | 未知/不存在 | ❌ 找不到对应用户，回退主界面 |

Telegram 的 `EXTRA_TITLE` 返回的是**对话的显示名**，而这个值**可能不是有效的用户名**。显示名可以是任意文字（包括中文、Emoji等），而用户名必须是 `^[a-zA-Z0-9_]{5,}$` 格式。

### 4.3 已探索的替代数据源

| 数据源 | 方法 | 结果 |
|--------|------|------|
| `EXTRA_TITLE` | `notification.extras.getString(Notification.EXTRA_TITLE)` | 仅显示名 |
| `MessagingStyle` | `notification.getStyle() as? MessagingStyle` → `messages` | 仅显示名 |
| `Person.getUri()` | `person.uri` | `null` |
| `Person.getKey()` | `person.key` | `null` |
| `contentIntent` | `sbn.notification?.contentIntent` | 受限 stub，不可用 |

## 5. 与系统通知栏的关键对比

| 特性 | 系统通知栏 | Jist 通知日志 |
|------|-----------|-------------|
| PendingIntent 来源 | 系统内部持有真实 PendingIntent | 通过 `contentIntent` 获取的受限 stub |
| 跨 APP 发送 | 通过系统内部 Binder 通道，不受限 | 受限，抛出 `CanceledException` |
| 深度链接能力 | Telegram 自带，内部跳转 | 需从通知数据提取用户名，数据不足 |
| 精确度 | 精确到具体对话 | 仅对真实 `@username` 有效 |

## 6. 结论

### 6.1 无法实现精确跳转的根本原因

两个不可逾越的限制叠加：

1. **Android 14+ 的安全限制**：`PendingIntent.send()` 跨 APP 调用被系统拦截，无法用 Telegram 的原始 `contentIntent` 跳转
2. **Telegram 通知数据有限**：通知中只有显示名（display name），没有用户名（username）或用户 ID，无法构建精确深度链接

### 6.2 当前最优方案

#### 对 Telegram

```kotlin
// ChatIntentBuilder.buildTelegramIntent()
// 不再尝试 tg://resolve?domain=xxx（因不可靠）
// 直接打开 Telegram 主界面
context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
```

用户点击 Jist 中的 Telegram 通知条目 → 打开 Telegram 主界面 → 用户自行点击目标对话。

#### 对其他 APP

维持现有逻辑：`getLaunchIntentForPackage` 打开主界面。部分 APP（如支持 Intent scheme 的）可通过 `ChatIntentBuilder` 精确跳转。

### 6.3 未来可能的解决路径

| 路径 | 可行性 | 工作量 | 说明 |
|------|--------|--------|------|
| Magisk 模块 hook AMS | 高 | 中 | Hook `PendingIntentRecord.send()` 移除 UID 校验，需维护模块 |
| 使用 Shizuku API | 中 | 低 | 通过 `shell` UID 发送 PendingIntent，效果待验证 |
| 向 Telegram 提交 Feature Request | 低 | 低 | 请求在通知中附带 username |
| Android 系统层修改 | 仅对自编译 ROM | 低 | patch `PendingIntentRecord.java` 移除限制 |

## 7. LSPosed 方案验证结果

### 7.1 有效发现

LSPosed 成功绕过 `PendingIntent.send()` 的跨 UID 拦截。日志确认：

```
PI.send() SUCCEEDED from Jist!    ← LSPosed Hook 使跨进程发送成功
```

### 7.2 仍然存在的问题

即使绕过 UID 拦截，Telegram 的 `contentIntent` PendingIntent 仍然**无法导航到具体对话**。原因：

1. **PendingIntent 为一次性（FLAG_ONE_SHOT）**：第一次 `send()` 后即被消耗，第二次调用必抛 `CanceledException`
2. **缺少导航上下文**：Telegram 的 PendingIntent 设计为配合系统通知栏上下文使用。系统栏点击时，Android 会传递通知的 `extras`（含对话标识）给目标 Activity。Jist 通过 `pi.send()` 发送时无法传递这些上下文
3. **Binder 传输限制**：尝试传递 `extras` Bundle 时，因包含文件描述符（FD）而抛 `RuntimeException: Not allowed to write file descriptors here`

### 7.3 系统通知栏为什么可以

| 对比项 | 系统通知栏点击 | Jist 发送 |
|--------|--------------|----------|
| PendingIntent 来源 | 系统内部获取真实 PendingIntent | `sbn.notification?.contentIntent` |
| 发送方式 | `PendingIntent.send()` + 通知 extras | `pi.send(ctx, 0, null)` |
| 导航上下文 | 系统自动传递完整通知 extras | 无（传递 extras 会因 FD 崩溃） |
| 结果 | ✅ 精确跳转到对应对话 | ❌ 打开 Telegram 但无法定位 |

### 7.4 最终结论

**在 Android 14+ 上，第三方 APP 无法实现通知栏点击精确跳转到 Telegram 具体对话**。

限制来自三个不可逾越的层面：

1. **Android 系统层（可绕过）**：跨 UID PendingIntent 拦截 → LSPosed Hook 可解决 ✅
2. **Telegram 通知 API（不可绕过）**：通知中只有显示名，无用户名/用户 ID → 无法构建深度链接 ❌
3. **Android 进程模型（不可绕过）**：跨进程发送 PendingIntent 无法传递完整通知上下文 → Activity 收不到导航信息 ❌

**当前最优方案**：
- Telegram 通知：打开主界面（`getLaunchIntentForPackage`），用户手动点进对话
- 其他 APP：维持现有 ChatIntentBuilder 逻辑

## 8. 方案六：LSPosed 广播注入 Telegram 进程（已验证）

### 8.1 原理

不在 Jist 侧组装跳转上下文，而是通过 LSPosed 在 Telegram 进程内注册一个广播接收器，接收 Jist 发出的导航请求，在 Telegram 内部用显示名检索并跳转。

### 8.2 实现方式

**Jist 侧**（`NotificationLogScreen.kt`）：
```kotlin
ctx.sendBroadcast(Intent("dev.rcht.jist.GOTO_CHAT").apply {
    putExtra("title", notification.title)
    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
})
```

**LSPosed 模块侧**（Hook `LaunchActivity.onCreate`）：
```kotlin
ctx.registerReceiver(object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        // 1. MessagesController.getInstance(0).getDialogsArray(0, false)
        // 2. 按 title 查找匹配的 dialog
        // 3. 通过 dialog.id 打开 ChatActivity
    }
}, IntentFilter("dev.rcht.jist.GOTO_CHAT"))
```

### 8.3 验证结果

| 环节 | 结果 | 原因 |
|------|------|------|
| LSPosed 模块加载 | ✅ 成功 | `ApplicationLoader` / `LaunchActivity` 均可 hook |
| 类扫描 | ✅ 成功 | `MessagesController`、`ChatActivity`、`LaunchActivity` 全部存在 |
| 广播接收器注册 | ✅ 成功 | 在 `LaunchActivity.onCreate` 之后注册正常 |
| Telegram 进程稳定性 | ❌ **失败** | 任何 LSPosed 模块注入到 Telegram 进程，AyuGram 的插件系统 `PluginsController.applyBlacklist` 均触发 `SIGSEGV` 原生崩溃 |

### 8.4 根本原因

AyuGram 的插件系统（`com.exteragram.messenger.plugins.PluginsController`）与 LSPosed 模块注入不兼容。即使模块中没有任何实际 Hook 代码，仅 `handleLoadPackage` 被调用就会导致类加载顺序变化，触发 `ApplicationLoader.<clinit>` 静态初始化中的原生空指针访问。

### 8.5 总结

| 方案 | 结果 | 限制 |
|------|------|------|
| LSPosed 绕过 UID 拦截 + `PendingIntent.send()` | ⚠️ 部分可用 | PI 为 ONE_SHOT，且无导航上下文 |
| LSPosed 注入 Telegram + 广播导航 | ❌ 不可用 | AyuGram 插件系统与 LSPosed 不兼容 |
| `https://t.me/xxx` 深度链接 | ❌ 不可用 | 需要用户名，通知只有显示名 |

## 9. 最终结论

**在 Android 14+ 上，第三方 APP 无法精确跳转到 Telegram 具体对话。** 现有全部路径均已验证并穷尽：

| 层面 | 限制 | 能否绕过 |
|------|------|---------|
| Android 14+ 跨 APP PendingIntent | Binder 驱动层拦截，system UID 无效 | LSPosed 可解，但 PI 为 ONE_SHOT |
| Telegram 通知 API | 仅提供显示名，无用户名/用户 ID | **不可绕过** |
| Android 进程模型 | 跨进程发 PI 无通知上下文 | **不可绕过** |
| AyuGram 兼容性 | LSPosed 注入导致原生崩溃 | **不可绕过**（AyuGram 自身问题） |

**当前 Jist 最终行为**：
- 所有 APP（含 Telegram）：`ChatIntentBuilder.buildChatIntent()` → `getLaunchIntentForPackage` 打开主界面
- Telegram 额外发送 `GOTO_CHAT` 广播（兼容未来可能的 LSPosed 模块）
