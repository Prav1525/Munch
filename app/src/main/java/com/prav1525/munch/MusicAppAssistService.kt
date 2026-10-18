package com.prav1525.munch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Narrow, user-enabled helper for the explicit voice command "play <title> on YouTube Music".
 * It only clicks a visible result whose accessible label matches the user's query.
 */
class MusicAppAssistService : AccessibilityService() {
    private var lastAttemptKey = ""
    private var lastAttemptAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != YOUTUBE_MUSIC_PACKAGE) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val query = prefs.getString(PENDING_QUERY, "").orEmpty().trim()
        if (query.isBlank()) return
        val now = System.currentTimeMillis()
        val key = query.lowercase(Locale.ROOT)
        if (key == lastAttemptKey && now - lastAttemptAt < 1800L) return
        lastAttemptKey = key
        lastAttemptAt = now
        val root = rootInActiveWindow ?: return
        try {
            val match = findMatchingResult(root, normalize(query))
            if (match != null) {
                val clicked = clickSelfOrClickableParent(match)
                match.recycle()
                if (clicked) {
                    prefs.edit().remove(PENDING_QUERY).apply()
                    Log.i(TAG, "Selected a visible YouTube Music result matching the requested title.")
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun findMatchingResult(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val label = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull().joinToString(" ").let(::normalize)
        val matches = label == query || (query.length >= 4 && label.contains(query))
        if (matches && (node.isClickable || hasClickableAncestor(node))) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = try { findMatchingResult(child, query) } finally { child.recycle() }
            if (found != null) return found
        }
        return null
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth++ < 6) {
            if (parent.isClickable) {
                parent.recycle()
                return true
            }
            val next = parent.parent
            parent.recycle()
            parent = next
        }
        parent?.recycle()
        return false
    }

    private fun clickSelfOrClickableParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth++ < 6) {
            if (parent.isClickable) {
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return success
            }
            val next = parent.parent
            parent.recycle()
            parent = next
        }
        parent?.recycle()
        return false
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    override fun onInterrupt() = Unit

    companion object {
        const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private const val PREFS = "munch_preferences"
        private const val PENDING_QUERY = "ytmusic_pending_query"
        private const val TAG = "MunchMusicAssist"

        fun isEnabled(context: android.content.Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val component = context.packageName + "/" + MusicAppAssistService::class.java.name
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }

        fun requestSearch(context: android.content.Context, query: String): Boolean {
            val clean = query.trim()
            if (clean.isBlank()) return false
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString(PENDING_QUERY, clean).apply()
            val uri = Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(clean))
            val appIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_MUSIC_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(appIntent); true }.getOrElse {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
            }
        }
    }
}
