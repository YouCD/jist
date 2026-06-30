# Notification Action Framework (NAF) 设计方案 v2.0

> 基于 LineageOS Android 16 (SDK 36, BP4A.251205.006) 源码分析完成

## 一、 系统架构与职责划分

为了实现真正的"代码复用"并避免在 `NotificationManagerService (NMS)` 中堆积业务逻辑，引入 **NotificationActionDispatcher** 作为统一的内核调度器，打破 `SystemUI` 与 `NotificationManagerService` 的强耦合。

```
       [ A I A g e n t ]                      [ SystemUI (StatusBar) ]
             │                                        │
    (Binder: INotificationManager)                    │ (Local Call)
             ▼                                        ▼
 [ NotificationManagerService ] ◄──────► [ NotificationActionDispatcher ]
             │
  (Fetch NotificationRecord)
             │
             ▼
 [ ActivityManager / PendingIntent ]
             │
  Content Intent / RemoteInput/Action
```

### 核心组件职责

- **NotificationManagerService (NMS)**：Binder 门户。负责基于 `key` 检索 `NotificationRecord`（活态或影子缓存），执行权限拦截、Shadow Cache 读写。
- **NotificationActionDispatcher (新增)**：无状态分发器。位于 `com.android.server.notification`，NMS 内部持有实例，负责解析 action 类型并执行相应的 `PendingIntent`/取消操作。
- **Shadow Cache (新增)**：`LruCache<String, NotificationRecord>` 缓存，解决通知被清除后 key 失效的"死区"问题。缓存容量 50，插入点在 `cancelNotificationLocked`。

> **注意**：本方案无 Framework 层的 `NotificationClickProcessor`。经源码检查 `packages/SystemUI/src/.../StatusBarNotificationActivityStarter.java`，SystemUI 的点击处理逻辑（动画、Keyguard Bouncer、WindowContainerToken）对 SystemUI 内部依赖极强，不适合抽取到 Framework。AI Agent 的虚拟点击只需发送 `PendingIntent`，因此直接调用 `intent.send()`。

> **关键前提：BAL（Background Activity Launch）豁免**
>
> NAF 的 `executeContentIntent` 通过 `PendingIntent.send()` 启动 Activity 时，调用方是 system_server（UID 1000）。原生 Android 对 PendingIntent 的 BAL 有 30 秒白名单窗口限制——通知发布 30 秒后，从后台触发该通知的 contentIntent 启动 Activity 会被 BAL 机制拦截。
>
> 为解决此问题，需修改 `frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java`，让 system caller 跳过 BAL 限制：
>
> ```java
> // 在 sendInner 方法中，获取 callingUid 后添加：
> final boolean isSystemCaller = callingUid == SYSTEM_UID || callingUid == ROOT_UID;
>
> // 在 startActivitiesInPackage / startActivityInPackage 调用处：
> final boolean balAllowed = isSystemCaller
>         || getBackgroundStartPrivilegesForActivitySender(allowlistToken)
>                 .allowsBackgroundActivityStarts();
> final PendingIntentRecord originatingPi = isSystemCaller ? null : this;
>
> // 传参时用 balAllowed 和 originatingPi 替代原来的内联表达式
> res = controller.mAtmInternal.startActivityInPackage(uid, callingPid,
>         callingUid, key.packageName, key.featureId, finalIntent,
>         resolvedType, resultTo, resultWho, requestCode, 0,
>         mergedOptions, userId, null, "PendingIntentRecord",
>         false /* validateIncomingUser */,
>         originatingPi, balAllowed);
> ```
>
> 此改动使 system_server 发送的 PendingIntent 不受 30 秒 BAL 白名单窗口约束，也不做 originating PI 的 creator BSP 检查。这对 NAF 的 Shadow Cache 场景尤为关键——通知被清除后，AI Agent 仍能在 30 秒窗口外触发 contentIntent。

## 二、 核心 API 与 AIDL 定义

### 1. 策略类：面向系统的公开/系统级 API

添加到 `frameworks/base/core/java/android/app/NotificationManager.java` 末尾、类结束大括号第 3605 行之前：

```java
/**
 * Action type for {@link #performNotificationAction}: open the content intent.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
public static final int ACTION_CONTENT = 1;

/**
 * Action type for {@link #performNotificationAction}: dismiss the notification.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
public static final int ACTION_DISMISS = 2;

/**
 * Action type for {@link #performNotificationAction}: trigger the reply action.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
public static final int ACTION_REPLY = 3;

/**
 * Action type for {@link #performNotificationAction}: mute the notification channel.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
public static final int ACTION_MUTE = 4;

/** @hide */
@IntDef(prefix = { "ACTION_" }, value = {
    ACTION_CONTENT, ACTION_DISMISS, ACTION_REPLY, ACTION_MUTE
})
@Retention(RetentionPolicy.SOURCE)
public @interface NotificationAction {}

/**
 * Programmatically perform an action on a notification identified by its key.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
@RequiresPermission(android.Manifest.permission.PERFORM_NOTIFICATION_ACTIONS)
public void performNotificationAction(@NonNull String key, @NotificationAction int action) {
    performNotificationAction(key, action, null);
}

/**
 * Programmatically perform an action on a notification identified by its key.
 * @hide
 */
@SystemApi
@SuppressLint("UnflaggedApi")
@RequiresPermission(android.Manifest.permission.PERFORM_NOTIFICATION_ACTIONS)
public void performNotificationAction(@NonNull String key, @NotificationAction int action,
        @Nullable Bundle extras) {
    try {
        getService().performNotificationAction(key, action, extras);
    } catch (RemoteException e) {
        throw e.rethrowFromSystemServer();
    }
}
```

> **注意**：`@SuppressLint("UnflaggedApi")` 用于抑制 API Lint 对新增未关联 Feature Flag 的 SystemApi 的报错。`@NonNull` 标注 `key` 参数以满足 nullability 检查。`import android.annotation.SuppressLint` 已存在于 NotificationManager.java 中。

### 2. IPC 层：`INotificationManager.aidl`

添加到 `frameworks/base/core/java/android/app/INotificationManager.aidl` 末尾、接口结束大括号第 276 行之前：

```aidl
/** @hide */
void performNotificationAction(String key, int action, in Bundle extras);
```

## 三、 核心 Pipeline 流程实现

### NotificationActionDispatcher

路径：`frameworks/base/services/core/java/com/android/server/notification/NotificationActionDispatcher.java`

```java
package com.android.server.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.os.Bundle;
import android.util.Slog;

public class NotificationActionDispatcher {
    private static final String TAG = "NotificationActionDispatcher";
    private static final boolean DEBUG = false;

    private final NotificationManagerService mNms;
    private final NmsCancelHelper mCancelHelper;

    public NotificationActionDispatcher(NotificationManagerService nms) {
        mNms = nms;
        mCancelHelper = new NmsCancelHelper(nms);
    }

    public void dispatchAction(NotificationRecord r, int action, Bundle extras,
            int callingUid, int callingPid) {
        if (r == null) {
            throw new IllegalArgumentException("NotificationRecord must not be null");
        }
        if (DEBUG) {
            Slog.d(TAG, "dispatchAction key=" + r.getKey() + " action=" + action);
        }
        switch (action) {
            case NotificationManager.ACTION_CONTENT:
                executeContentIntent(r, callingUid, callingPid);
                break;
            case NotificationManager.ACTION_DISMISS:
                executeDismiss(r, callingUid, callingPid);
                break;
            case NotificationManager.ACTION_REPLY:
                executeReplyAction(r, extras);
                break;
            case NotificationManager.ACTION_MUTE:
                executeMute(r);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action: " + action);
        }
    }

    private void executeContentIntent(NotificationRecord r,
            int callingUid, int callingPid) {
        Notification notification = r.getNotification();
        PendingIntent intent = notification.contentIntent;
        if (intent == null) {
            Slog.w(TAG, "No contentIntent for " + r.getKey());
            return;
        }
        try {
            intent.send(mNms.getContext(), 0, null, null, null, null, null);
            Slog.i(TAG, "executeContentIntent sent key=" + r.getKey()
                    + " pkg=" + r.getSbn().getPackageName());
            if ((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                mCancelHelper.cancel(r, callingUid, callingPid);
            }
        } catch (PendingIntent.CanceledException e) {
            Slog.e(TAG, "Failed to execute content intent for " + r.getKey(), e);
        }
    }

    private void executeReplyAction(NotificationRecord r, Bundle extras) {
        if (extras == null || !extras.containsKey(RemoteInput.EXTRA_RESULTS_DATA)) {
            throw new IllegalArgumentException(
                    "ACTION_REPLY requires extras with RemoteInput.EXTRA_RESULTS_DATA");
        }
        Notification.Action[] actions = r.getNotification().actions;
        if (actions == null) {
            Slog.w(TAG, "No actions for " + r.getKey());
            return;
        }
        for (Notification.Action action : actions) {
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs == null) continue;
            PendingIntent intent = action.actionIntent;
            if (intent == null) continue;
            Bundle resultBundle = extras.getBundle(RemoteInput.EXTRA_RESULTS_DATA);
            android.content.Intent fillIn = new android.content.Intent();
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, resultBundle);
            try {
                intent.send(mNms.getContext(), 0, fillIn, null, null);
                Slog.i(TAG, "executeReplyAction sent key=" + r.getKey()
                        + " pkg=" + r.getSbn().getPackageName());
            } catch (PendingIntent.CanceledException e) {
                Slog.e(TAG, "Reply action PendingIntent canceled for " + r.getKey(), e);
            }
            return;
        }
        Slog.w(TAG, "No RemoteInput action found for " + r.getKey());
    }

    private void executeDismiss(NotificationRecord r,
            int callingUid, int callingPid) {
        Slog.i(TAG, "executeDismiss key=" + r.getKey()
                + " pkg=" + r.getSbn().getPackageName());
        mCancelHelper.cancel(r, callingUid, callingPid);
    }

    private void executeMute(NotificationRecord r) {
        Slog.i(TAG, "ACTION_MUTE not yet implemented for " + r.getKey());
    }
}
```

### NmsCancelHelper 辅助类

同路径，用于在 Dispatcher 中调用 NMS 包内方法：

```java
package com.android.server.notification;

import static android.service.notification.NotificationListenerService.REASON_CANCEL;

import android.app.Notification;
import android.util.Slog;

public class NmsCancelHelper {
    private static final String TAG = "NotificationActionDispatcher";
    private final NotificationManagerService mNms;

    public NmsCancelHelper(NotificationManagerService nms) {
        mNms = nms;
    }

    public void cancel(NotificationRecord r, int callingUid, int callingPid) {
        Slog.i(TAG, "cancel key=" + r.getKey() + " pkg=" + r.getSbn().getPackageName()
                + " from uid=" + callingUid);
        mNms.cancelNotification(
                callingUid, callingPid,
                r.getSbn().getPackageName(),
                r.getSbn().getTag(),
                r.getSbn().getId(),
                0,
                android.app.Notification.FLAG_NO_DISMISS,
                true,
                r.getUserId(),
                REASON_CANCEL,
                null);
    }
}
```

### NMS 中的 Binder 实现

在 `INotificationManager.Stub` 匿名内部类（NMS 第 4135 行）中添加：

```java
@Override
public void performNotificationAction(String key, int action, Bundle extras) {
    enforceSystemOrSystemUI("performNotificationAction");
    int callingUid = Binder.getCallingUid();
    int callingPid = Binder.getCallingPid();
    Slog.i(TAG, "performNotificationAction key=" + key + " action=" + action
            + " from uid=" + callingUid + " pid=" + callingPid);
    long identity = Binder.clearCallingIdentity();
    try {
        NotificationRecord r = getNotificationRecord(key);
        if (r == null) {
            synchronized (mDismissedNotificationCache) {
                r = mDismissedNotificationCache.get(key);
            }
            if (r != null) {
                Slog.i(TAG, "performNotificationAction shadow cache hit for key=" + key);
            }
        }
        if (r == null) {
            Slog.w(TAG, "performNotificationAction key not found: " + key);
            throw new IllegalArgumentException(
                    "Notification key not found: " + key);
        }
        int callingUserId = UserHandle.getUserId(callingUid);
        if (r.getUserId() != callingUserId
                && callingUserId != UserHandle.USER_SYSTEM) {
            Slog.w(TAG, "performNotificationAction cross-user denied: key=" + key
                    + " notifUser=" + r.getUserId() + " callerUser=" + callingUserId);
            throw new SecurityException(
                    "Cross-user notification action denied.");
        }
        mActionDispatcher.dispatchAction(
                r, action, extras, callingUid, callingPid);
    } finally {
        Binder.restoreCallingIdentity(identity);
    }
}
```

## 四、 Shadow Cache：解决通知被清除后的"死区"问题

### 实现位置

在 `NotificationManagerService.java` 中新增 import 并添加字段。

NMS 第 346-357 行的 `android.util.*` import 区段中补充：

```java
import android.util.LruCache;
```

NMS 第 735 行 `mNotificationsByKey` 之后添加 Shadow Cache 字段，以及 `mActionDispatcher` 字段：

```java
@GuardedBy("mNotificationLock")
final ArrayMap<String, NotificationRecord> mNotificationsByKey = new ArrayMap<>();

// Shadow Cache：缓存已被取消的通知，容量 50
private final LruCache<String, NotificationRecord> mDismissedNotificationCache =
        new LruCache<>(50);

// NotificationActionDispatcher 实例
final NotificationActionDispatcher mActionDispatcher;
```

在 NMS 构造函数末尾（第 2661 行之后）初始化 Dispatcher：

```java
mActionDispatcher = new NotificationActionDispatcher(this);
```

### 插入点

在 `removeFromNotificationListsLocked` 内部（NMS 第 11280 行），`mNotificationsByKey.remove(...)` 之后——一处修改覆盖全部 3 条取消路径（`removeFlagFromNotificationLocked`、`snoozeNotificationLocked`、`CancelNotificationRunnable`）：

```java
// removeFromNotificationListsLocked 中，mNotificationsByKey.remove 之后
if (recordInList.getNotification().contentIntent != null) {
    Slog.d(TAG, "Shadow Cache put key=" + recordInList.getKey()
            + " pkg=" + recordInList.getSbn().getPackageName());
    mDismissedNotificationCache.put(recordInList.getKey(), recordInList);
}
```

> **注意**：`removeFromNotificationListsLocked()` 不在 `cancelNotificationLocked` 内部调用，而是由 3 个调用者各自在调用 `cancelNotificationLocked` **之前**执行。因此插入点必须在 `removeFromNotificationListsLocked` 内部，而非 `cancelNotificationLocked` 中。

### 清理策略

- LruCache 自动保证最多 50 条，越早的项越先被回收
- 只要 App 进程存在、PendingIntent 未被 cancel，缓存中引用的 Intent 依旧有效

## 五、 安全与隔离模型

### 声明系统级权限

在 `frameworks/base/core/res/AndroidManifest.xml` 中添加：

```xml
<permission android:name="android.permission.PERFORM_NOTIFICATION_ACTIONS"
    android:protectionLevel="signature|privileged"
    android:label="@string/permlab_performNotificationActions"
    android:description="@string/permdesc_performNotificationActions" />
```

### 权限字符串资源

在 `frameworks/base/core/res/res/values/strings.xml` 中添加（注意是双层 `res/res` 路径）：

```xml
<string name="permlab_performNotificationActions">perform notification actions</string>
<string name="permdesc_performNotificationActions">Allows the app to programmatically perform actions on notifications (content click, dismiss, reply).</string>
```

### 权限检查策略

Binder 入口使用 NMS 既有惯例 `enforceSystemOrSystemUI()`，而非 `checkPermission`。

## 六、 实现清单

| # | 文件 | 工作 | 行数 |
|---|------|------|------|
| 1 | `frameworks/base/core/res/AndroidManifest.xml` | 添加权限声明 | ~7 |
| 2 | `frameworks/base/core/res/res/values/strings.xml` | 添加权限字符串资源 | ~2 |
| 3 | `frameworks/base/core/java/android/app/INotificationManager.aidl` | 添加 AIDL 方法 | ~1 |
| 4 | `frameworks/base/core/java/android/app/NotificationManager.java` | 添加 public API + IntDef + @SuppressLint + @NonNull | ~60 |
| 5 | `frameworks/base/services/core/java/com/android/server/notification/NotificationActionDispatcher.java` | 新建 Dispatcher | ~150 |
| 6 | `frameworks/base/services/core/java/com/android/server/notification/NmsCancelHelper.java` | 新建取消辅助类 | ~50 |
| 7 | `frameworks/base/services/core/java/com/android/server/notification/NotificationManagerService.java` | import + 字段 + Binder + Shadow Cache + 初始化 | ~70 |
| 8 | `frameworks/base/services/core/java/com/android/server/notification/NotificationShellCmd.java` | 新增 `perform_action` 子命令 | ~20 |
| 9 | `frameworks/base/services/core/java/com/android/server/am/PendingIntentRecord.java` | system caller 跳过 BAL 限制 | ~10 |
| 10 | `frameworks/base/core/api/system-current.txt` | 注册新 API 定义（自动生成） | ~7 |
| 11 | `frameworks/base/core/api/system-lint-baseline.txt` | 注册 lint baseline（自动生成） | ~2 |
| 合计 | 11 文件 | 全部新增或末尾追加 | ~380 |

## 七、 日志体系

所有关键路径均添加了 `Slog` 日志，tag 为 `NotificationService`（NMS）或 `NotificationActionDispatcher`（Dispatcher/Helper）：

| 位置 | 级别 | 日志内容 |
|------|------|---------|
| NMS Binder 入口 | `I` | `performNotificationAction key=... action=... from uid=... pid=...` |
| Shadow Cache 命中 | `I` | `performNotificationAction shadow cache hit for key=...` |
| key 未找到 | `W` | `performNotificationAction key not found: ...` |
| 跨用户拒绝 | `W` | `performNotificationAction cross-user denied: ...` |
| Shadow Cache 写入 | `D` | `Shadow Cache put key=... pkg=...` |
| Content Intent 发送 | `I` | `executeContentIntent sent key=... pkg=...` |
| Reply 发送 | `I` | `executeReplyAction sent key=... pkg=...` |
| Dismiss 执行 | `I` | `executeDismiss key=... pkg=...` |
| Cancel 执行 | `I` | `cancel key=... pkg=... from uid=...` |

查看日志：
```bash
adb logcat -s NotificationService:V NotificationActionDispatcher:V
```

## 八、 测试验证

### Shell 命令测试

在 `NotificationShellCmd.java` 中新增了 `perform_action` 子命令，可通过 adb 直接测试：

```bash
# 1. 发一个测试通知
adb shell cmd notification post test_tag "Test notification"

# 2. 获取通知 key
adb shell cmd notification list
# 输出类似: 0|android|test_tag|0|10089|10089

# 3. 触发 ACTION_CONTENT（模拟点击通知）
adb shell cmd notification perform_action <key> content

# 4. 触发 ACTION_DISMISS（模拟清除通知）
adb shell cmd notification perform_action <key> dismiss

# 5. 测试 Shadow Cache：先 dismiss 再 content
adb shell cmd notification post test_tag2 "Shadow cache test"
adb shell cmd notification list  # 获取 key2
adb shell cmd notification perform_action <key2> dismiss  # 先清除
adb shell cmd notification perform_action <key2> content  # 再从 Shadow Cache 操作
# 期望: 不会报 "key not found"，日志会显示 "shadow cache hit"
```

### 权限验证

```bash
# 确认权限已注册
adb shell pm list permissions | grep PERFORM_NOTIFICATION_ACTIONS
# 期望输出: android.permission.PERFORM_NOTIFICATION_ACTIONS
```

### 推送更新

使用 `push_naf.sh` 脚本一键推送所有编译产物到设备：

```bash
cd /aosp/LineageOS && ./push_naf.sh
```

需推送的文件（9 个）：
- `framework.jar` + `boot-framework.vdex` + `boot-framework.art` + `boot-framework.oat`
- `framework-res.apk`
- `services.jar` + `services.art` + `services.odex` + `services.vdex`

## 九、 优劣势评估

### 优势

1. **非侵入性**：全部为新增文件和末尾追加，不修改任何现有代码
2. **与 SystemUI 解耦**：不需要抽取 SystemUI 内部动画/Bouncer 逻辑
3. **Shadow Cache 透明**：不影响系统原有通知清理和通知监听机制
4. **行为日志一致性**：走 NMS 统一路径，AppOps 和 Digital Wellbeing 不受影响

### 风险与对策

| 风险 | 对策 |
|------|------|
| Keyguard 锁屏下虚拟点击无效 | Framework 不做解锁，由调用方确认设备解锁状态；锁屏时抛 SecurityException |
| 权限被 SystemUI 滥用 | signature|privileged 限制，加上 enforceSystemOrSystemUI 双层保护 |
| Shadow Cache 持有 PendingIntent 时间过长 | LruCache FIFO 自动淘汰旧条目 |
