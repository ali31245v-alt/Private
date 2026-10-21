package com.at.shield

import android.accessibilityservice.AccessibilityService
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuardService : AccessibilityService() {
    private var obs: ContentObserver? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        DnsLock.apply(this)
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { DnsLock.apply(this@GuardService) }
        }
        obs = o
        try {
            contentResolver.registerContentObserver(Settings.Global.getUriFor("private_dns_mode"), false, o)
            contentResolver.registerContentObserver(Settings.Global.getUriFor("private_dns_specifier"), false, o)
        } catch (_: Exception) { }
    }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        obs?.let { try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) { } }
        return super.onUnbind(intent)
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!Store.enabled(this)) return
        DnsLock.apply(this)
        val pkg = e?.packageName?.toString() ?: return
        if (pkg == packageName || pkg.contains("systemui") || pkg.contains("inputmethod")) return
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, 0)
        val t = sb.toString().lowercase()
        val inSystem = pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller") || pkg.contains("deviceadmin")
        if (inSystem && !Store.isUnlocked(this)) {
            val sensitive = listOf("at shield", "vpn", "private dns", "dns الخاص", "device admin", "مسؤولي الجهاز", "تطبيقات مشرف الجهاز", "accessibility", "إمكانية الوصول", "date & time", "date and time", "التاريخ والوقت")
            if (sensitive.any { t.contains(it) }) { performGlobalAction(GLOBAL_ACTION_HOME); return }
        }
        if (Shield.screenKeywords.any { t.contains(it) }) performGlobalAction(GLOBAL_ACTION_HOME)
    }
    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (n == null || depth > 30 || sb.length > 20000) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, depth + 1)
    }
    override fun onInterrupt() {}
}