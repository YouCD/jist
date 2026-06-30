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

import static android.service.notification.NotificationListenerService.REASON_CANCEL;

import android.app.Notification;
import android.util.Slog;

/**
 * Thin helper that invokes the package-private
 * {@link NotificationManagerService#cancelNotification} overload from within
 * the same package.  Kept as a separate class so that
 * {@link NotificationActionDispatcher} stays focused on action dispatch.
 *
 * @hide
 */
public class NmsCancelHelper {
    private static final String TAG = "NotificationActionDispatcher";

    private final NotificationManagerService mNms;

    public NmsCancelHelper(NotificationManagerService nms) {
        mNms = nms;
    }

    /**
     * Cancel the given notification record as if the original caller requested it.
     *
     * @param r           the notification record to cancel
     * @param callingUid  uid of the original binder caller
     * @param callingPid  pid of the original binder caller
     */
    public void cancel(NotificationRecord r, int callingUid, int callingPid) {
        Slog.i(TAG, "cancel key=" + r.getKey() + " pkg=" + r.getSbn().getPackageName()
                + " from uid=" + callingUid);
        mNms.cancelNotification(
                callingUid,
                callingPid,
                r.getSbn().getPackageName(),
                r.getSbn().getTag(),
                r.getSbn().getId(),
                /* mustHaveFlags= */ 0,
                /* mustNotHaveFlags= */ Notification.FLAG_NO_DISMISS,
                /* sendDelete= */ true,
                r.getUserId(),
                REASON_CANCEL,
                /* listener= */ null);
    }
}
