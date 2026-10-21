package com.at.shield

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings

object DnsLock {
    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(c: Context) = ComponentName(c, AdminRcv::class.java)
    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)
    fun canWrite(c: Context) = c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    private fun restrictions(): List<String> {
        val l = mutableListOf(UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_SAFE_BOOT)
        if (Build.VERSION.SDK_INT >= 29) l.add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        if (Build.VERSION.SDK_INT >= 28) {
            l.add(UserManager.DISALLOW_CONFIG_DATE_TIME)
            l.add(UserManager.DISALLOW_APPS_CONTROL)
        }
        return l
    }
    fun apply(c: Context) {
        if (!Store.enabled(c) || Store.isUnlocked(c)) return
        if (canWrite(c)) try {
            val cr = c.contentResolver
            if (Settings.Global.getString(cr, "private_dns_mode") != "off") Settings.Global.putString(cr, "private_dns_mode", "off")
        } catch (_: Exception) { }
        if (isOwner(c)) lockOwner(c)
    }
    private fun lockOwner(c: Context) {
        try {
            val d = dpm(c); val a = admin(c)
            val cur = d.getUserRestrictions(a)
            val missing = restrictions().filter { !cur.getBoolean(it) }
            if (missing.isNotEmpty()) {
                missing.forEach { d.addUserRestriction(a, it) }
                d.setUninstallBlocked(a, c.packageName, true)
                try { d.setAlwaysOnVpnPackage(a, c.packageName, true) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
    fun unlockOwner(c: Context) {
        if (!isOwner(c)) return
        try {
            val d = dpm(c); val a = admin(c)
            restrictions().forEach { d.clearUserRestriction(a, it) }
            d.setUninstallBlocked(a, c.packageName, false)
            try { d.setAlwaysOnVpnPackage(a, null, false) } catch (_: Exception) { }
        } catch (_: Exception) { }
    }
    fun removeOwner(c: Context) {
        if (!isOwner(c)) return
        unlockOwner(c)
        try { dpm(c).clearDeviceOwnerApp(c.packageName) } catch (_: Exception) { }
    }
}