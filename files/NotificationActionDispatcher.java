/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.os.Bundle;
import android.util.Slog;

/**
 * Dispatches notification actions (content click, dismiss, reply) on behalf of
 * privileged callers.  Runs inside the system_server process and operates on
 * {@link NotificationRecord} objects obtained from the active list or the
 * Shadow Cache.
 *
 * @hide
 */
public class NotificationActionDispatcher {
    private static final String TAG = "NotificationActionDispatcher";
    private static final boolean DEBUG = false;

    private final NotificationManagerService mNms;
    private final NmsCancelHelper mCancelHelper;

    public NotificationActionDispatcher(NotificationManagerService nms) {
        mNms = nms;
        mCancelHelper = new NmsCancelHelper(nms);
    }

    /**
     * Execute the requested action on the given notification record.
     *
     * @param r           the notification record (must be non-null)
     * @param action      one of {@link NotificationManager#ACTION_CONTENT},
     *                    {@link NotificationManager#ACTION_DISMISS},
     *                    {@link NotificationManager#ACTION_REPLY},
     *                    {@link NotificationManager#ACTION_MUTE}
     * @param extras      optional extras bundle (required for ACTION_REPLY)
     * @param callingUid  the uid of the original binder caller
     * @param callingPid  the pid of the original binder caller
     */
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
                throw new IllegalArgumentException("Unsupported notification action: " + action);
        }
    }

    private void executeContentIntent(NotificationRecord r, int callingUid, int callingPid) {
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
                Slog.d(TAG, "executeContentIntent auto-cancel key=" + r.getKey());
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
            if (remoteInputs == null) {
                continue;
            }
            PendingIntent intent = action.actionIntent;
            if (intent == null) {
                continue;
            }
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

    private void executeDismiss(NotificationRecord r, int callingUid, int callingPid) {
        Slog.i(TAG, "executeDismiss key=" + r.getKey()
                + " pkg=" + r.getSbn().getPackageName());
        mCancelHelper.cancel(r, callingUid, callingPid);
    }

    private void executeMute(NotificationRecord r) {
        Slog.i(TAG, "ACTION_MUTE not yet implemented for " + r.getKey());
    }
}
