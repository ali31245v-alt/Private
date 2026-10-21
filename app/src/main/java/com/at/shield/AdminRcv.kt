package com.at.shield

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminRcv : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "تعطيل الحماية غير مسموح بدون كلمة السر"
}