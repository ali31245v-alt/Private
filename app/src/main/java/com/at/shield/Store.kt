package com.at.shield

import android.content.Context
import java.security.MessageDigest

object Store {
    const val DELAY_MS = 24L * 60 * 60 * 1000
    const val WINDOW_MS = 6L * 60 * 60 * 1000
    private const val MAX_FAILS = 5
    private const val LOCK_MS = 30L * 60 * 1000
    private fun p(c: Context) = c.getSharedPreferences("shield", Context.MODE_PRIVATE)
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(("at_shield_salt_" + s).toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun hasPin(c: Context) = p(c).contains("pin")
    fun setPin(c: Context, pin: String) = p(c).edit().putString("pin", hash(pin)).apply()
    fun lockLeft(c: Context): Long = maxOf(0L, p(c).getLong("lockUntil", 0L) - System.currentTimeMillis())
    fun checkPin(c: Context, pin: String): Boolean {
        if (lockLeft(c) > 0) return false
        val ok = p(c).getString("pin", null) == hash(pin)
        val e = p(c).edit()
        if (ok) e.putInt("fails", 0) else {
            val f = p(c).getInt("fails", 0) + 1
            if (f >= MAX_FAILS) e.putInt("fails", 0).putLong("lockUntil", System.currentTimeMillis() + LOCK_MS)
            else e.putInt("fails", f)
        }
        e.apply()
        return ok
    }
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun unlockedUntil(c: Context) = p(c).getLong("unlock", 0L)
    fun unlockFor(c: Context, minutes: Int) = p(c).edit().putLong("unlock", System.currentTimeMillis() + minutes * 60_000L).apply()
    fun isUnlocked(c: Context) = System.currentTimeMillis() < unlockedUntil(c)
    private fun reqAt(c: Context) = p(c).getLong("req", 0L)
    fun reqState(c: Context): Int {
        val r = reqAt(c)
        if (r == 0L) return 0
        val n = System.currentTimeMillis()
        return when {
            n < r + DELAY_MS -> 1
            n <= r + DELAY_MS + WINDOW_MS -> 2
            else -> { cancelRequest(c); 0 }
        }
    }
    fun startRequest(c: Context) { if (reqState(c) == 0) p(c).edit().putLong("req", System.currentTimeMillis()).apply() }
    fun cancelRequest(c: Context) = p(c).edit().remove("req").apply()
    fun waitLeft(c: Context): Long = maxOf(0L, reqAt(c) + DELAY_MS - System.currentTimeMillis())
    fun windowLeft(c: Context): Long = maxOf(0L, reqAt(c) + DELAY_MS + WINDOW_MS - System.currentTimeMillis())
    fun custom(c: Context): Set<String> = p(c).getStringSet("custom", emptySet()) ?: emptySet()
    fun addCustom(c: Context, d: String) {
        val clean = d.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
        if (clean.isNotEmpty()) p(c).edit().putStringSet("custom", custom(c) + clean).apply()
    }
}