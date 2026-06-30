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

## 二、 核心 API 与 AIDL 定义

### 1. 策略类：面向系统的公开/系统级 API

添加到 `frameworks/base/core/java/android/app/NotificationManager.java` 末尾、类结束大括号第 3605 行之前：

```java
/** @hide */
@SystemApi
public static final int ACTION_CONTENT = 1;
/** @hide */
@SystemApi
public static final int ACTION_DISMISS = 2;
/** @hide */
@SystemApi
public static final int ACTION_REPLY = 3;
/** @hide */
@SystemApi
public static final int ACTION_MUTE = 4;

/** @hide */
@IntDef(prefix = { "ACTION_" }, value = {
    ACTION_CONTENT, ACTION_DISMISS, ACTION_REPLY, ACTION_MUTE
})
@Retention(RetentionPolicy.SOURCE)
public @interface NotificationAction {}

@RequiresPermission(android.Manifest.permission.PERFORM_NOTIFICATION_ACTIONS)
public void performNotificationAction(String key, @NotificationAction int action) {
    performNotificationAction(key, action, null);
}

@RequiresPermission(android.Manifest.permission.PERFORM_NOTIFICATION_ACTIONS)
public void performNotificationAction(String key, @NotificationAction int action,
        @Nullable Bundle extras) {
    try {
        getService().performNotificationAction(key, action, extras);
    } catch (RemoteException e) {
        throw e.rethrowFromSystemServer();
    }
}
```

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
import android.os.Bundle;
import android.app.RemoteInput;
import android.os.UserHandle;
import android.util.Slog;

public class NotificationActionDispatcher {
    private static final String TAG = "NotificationActionDispatcher";
    private final NotificationManagerService mNms;
    private final NmsCancelRunnable mCancel;

    public NotificationActionDispatcher(NotificationManagerService nms) {
        mNms = nms;
        mCancel = new NmsCancelRunnable(nms);
    }

    public void dispatchAction(NotificationRecord r, int action, Bundle extras,
            int callingUid, int callingPid) {
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
        PendingIntent intent = r.getNotification().contentIntent;
        if (intent == null) return;
        try {
            intent.send(mNms.getContext(), 0, null, null, null, null, null);
            if ((r.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                mCancel.cancel(r, callingUid, callingPid);
            }
        } catch (PendingIntent.CanceledException e) {
            Slog.e(TAG, "Failed to execute content intent for " + r.getKey(), e);
        }
    }

    private void executeReplyAction(NotificationRecord r, Bundle extras) {
        if (extras == null || !extras.containsKey(
                RemoteInput.EXTRA_RESULTS_DATA)) {
            throw new IllegalArgumentException(
                    "ACTION_REPLY requires EXTRA_RESULTS_DATA");
        }
        Notification.Action[] actions = r.getNotification().actions;
        if (actions == null) return;
        for (Notification.Action action : actions) {
            RemoteInput[] remoteInputs = action.getRemoteInputs();
            if (remoteInputs == null) continue;
            PendingIntent intent = action.actionIntent;
            if (intent == null) continue;
            Bundle resultBundle = extras.getBundle(
                    RemoteInput.EXTRA_RESULTS_DATA);
            android.content.Intent fillIn = new android.content.Intent();
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, resultBundle);
            try {
                intent.send(mNms.getContext(), 0, fillIn, null, null);
            } catch (PendingIntent.CanceledException e) {
                Slog.e(TAG, "Reply action PendingIntent canceled", e);
            }
            break;
        }
    }

    private void executeDismiss(NotificationRecord r,
            int callingUid, int callingPid) {
        mCancel.cancel(r, callingUid, callingPid);
    }

    private void executeMute(NotificationRecord r) {
        Slog.i(TAG, "ACTION_MUTE not yet implemented");
    }
}
```

### NmsCancelRunnable 辅助类

同路径，用于在 Dispatcher 中调用 NMS 包内方法：

```java
package com.android.server.notification;

import static android.service.notification.NotificationListenerService.REASON_CANCEL;

public class NmsCancelRunnable {
    private final NotificationManagerService mNms;

    public NmsCancelRunnable(NotificationManagerService nms) {
        mNms = nms;
    }

    public void cancel(NotificationRecord r, int callingUid, int callingPid) {
        // 复用 NMS 的 package-visible 方法，异步取消
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
    long token = Binder.clearCallingIdentity();
    try {
        NotificationRecord r = getNotificationRecord(key);
        if (r == null) {
            synchronized (mDismissedNotificationCache) {
                r = mDismissedNotificationCache.get(key);
            }
        }
        if (r == null) {
            throw new IllegalArgumentException(
                    "Notification key not found: " + key);
        }
        int callingUserId = UserHandle.getUserId(callingUid);
        if (r.getUserId() != callingUserId
                && callingUserId != UserHandle.USER_SYSTEM) {
            throw new SecurityException(
                    "Cross-user notification action is denied.");
        }
        mActionDispatcher.dispatchAction(
                r, action, extras, callingUid, callingPid);
    } finally {
        Binder.restoreCallingIdentity(token);
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
if (recordInList != null
        && recordInList.getNotification().contentIntent != null) {
    synchronized (mDismissedNotificationCache) {
        mDismissedNotificationCache.put(recordInList.getKey(), recordInList);
    }
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

在 `frameworks/base/core/res/values/strings.xml` 中添加：

```xml
<string name="permlab_performNotificationActions">执行通知操作</string>
<string name="permdesc_performNotificationActions">允许应用以编程方式对通知执行操作（点击、关闭、回复）。</string>
```

### 权限检查策略

Binder 入口使用 NMS 既有惯例 `enforceSystemOrSystemUI()`，而非 `checkPermission`。

## 六、 实现清单

| # | 文件 | 工作 | 行数 |
|---|------|------|------|
| 1 | `frameworks/base/core/res/AndroidManifest.xml` | 添加权限声明 | ~7 |
| 2 | `frameworks/base/core/java/android/app/INotificationManager.aidl` | 添加 AIDL 方法 | ~1 |
| 3 | `frameworks/base/core/java/android/app/NotificationManager.java` | 添加 public API | ~35 |
| 4 | `frameworks/base/services/.../NotificationActionDispatcher.java` | 新建 Dispatcher | ~110 |
| 5 | `frameworks/base/services/.../NmsCancelRunnable.java` | 新建辅助类 | ~30 |
| 6 | `frameworks/base/services/.../NotificationManagerService.java` | Binder + Shadow Cache | ~60 |
| 7 | `frameworks/base/core/res/values/strings.xml` | 权限字符串 | ~2 |
| 合计 | 7 文件 | 全部新增或末尾追加 | ~245 |

## 七、 优劣势评估

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
