package com.prav1525.munch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object PhoneAgentController {
    const val ACTION_RUN_TASK = "com.prav1525.munch.action.RUN_PHONE_AGENT"
    private const val PREFS = "munch_phone_agent"
    private const val PENDING = "pending_goal"

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val name = splitter.next()
            if (name.equals(context.packageName + "/.PhoneAgentService", true) ||
                name.equals(context.packageName + "/" + PhoneAgentService::class.java.name, true)) return true
        }
        return false
    }

    /** Returns true if the service is enabled and the request was dispatched. */
    fun request(context: Context, goal: String): Boolean {
        val clean = goal.trim().take(500)
        if (clean.isBlank() || isHighRisk(clean)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PENDING, clean).apply()
        if (!isEnabled(context)) return false
        context.sendBroadcast(Intent(ACTION_RUN_TASK).setPackage(context.packageName).putExtra("goal", clean))
        return true
    }

    fun takePendingGoal(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val goal = prefs.getString(PENDING, null)
        prefs.edit().remove(PENDING).apply()
        return goal
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun isHighRisk(goal: String): Boolean {
        val lower = goal.lowercase()
        return listOf("send money", "transfer money", "make a payment", "pay for", "purchase", "buy ",
            "checkout", "enter password", "type password", "delete account", "factory reset", "place an order")
            .any { lower.contains(it) }
    }
}