package com.prav1525.munch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Spotify Web API using Authorization Code with PKCE; no client secret is embedded. */
object SpotifyAgent {
    private const val PREFS = "munch_preferences"
    private const val CLIENT_ID = "spotify_client_id"
    private const val ACCESS_TOKEN = "spotify_access_token"
    private const val REFRESH_TOKEN = "spotify_refresh_token"
    private const val EXPIRES_AT = "spotify_expires_at"
    private const val STATE = "spotify_oauth_state"
    private const val VERIFIER = "spotify_pkce_verifier"
    const val REDIRECT_URI = "munch://spotify-callback"
    private val io = Executors.newSingleThreadExecutor()
    private val random = SecureRandom()

    fun saveClientId(context: Context, clientId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLIENT_ID, clientId.trim()).apply()
    }
    fun getClientId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLIENT_ID, "").orEmpty()
    fun isConnected(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(REFRESH_TOKEN, null) != null

    fun connect(context: Context): Boolean {
        val clientId = getClientId(context)
        if (clientId.isBlank()) return false
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(24)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VERIFIER, verifier).putString(STATE, state).apply()
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "user-read-playback-state user-modify-playback-state user-read-currently-playing")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun handleRedirect(context: Context, uri: Uri?, onComplete: (() -> Unit)? = null): Boolean {
        if (uri == null || uri.scheme != "munch" || uri.host != "spotify-callback") return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getString(STATE, "").orEmpty()
        val received = uri.getQueryParameter("state").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty()
        val error = uri.getQueryParameter("error").orEmpty()
        if (expected.isBlank() || expected != received || code.isBlank()) {
            prefs.edit().remove(STATE).remove(VERIFIER)
                .putString("spotify_status", if (error.isNotBlank()) "Spotify authorization denied: " + error else "Spotify sign-in check failed; connect again.")
                .apply()
            onComplete?.invoke()
            return true
        }
        val verifier = prefs.getString(VERIFIER, "").orEmpty()
        val clientId = getClientId(context)
        prefs.edit().remove(STATE).remove(VERIFIER).apply()
        io.execute {
            val status = runCatching {
                val response = tokenRequest(
                    "grant_type" to "authorization_code", "code" to code,
                    "redirect_uri" to REDIRECT_URI, "client_id" to clientId, "code_verifier" to verifier
                )
                saveTokenResponse(context, response)
                "Spotify connected. Playback commands need Premium and an active Spotify device."
            }.getOrElse { "Spotify connection failed: " + (it.message ?: "unknown error").take(180) }
            prefs.edit().putString("spotify_status", status).apply()
            android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete?.invoke() }
        }
        return true
    }

    fun getStatus(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("spotify_status", if (isConnected(context)) "Spotify connected" else "Not connected").orEmpty()

    fun searchAndPlay(context: Context, query: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val result = runCatching {
                val token = validAccessToken(context)
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val search = apiRequest("GET", "https://api.spotify.com/v1/search?q=" + encoded + "&type=track&limit=5", token)
                val tracks = search.optJSONObject("tracks")?.optJSONArray("items") ?: JSONArray()
                if (tracks.length() == 0) throw IllegalStateException("Spotify could not find a matching track.")
                val track = tracks.optJSONObject(0) ?: throw IllegalStateException("Spotify returned an invalid track.")
                val trackUri = track.optString("uri")
                val title = track.optString("name", query)
                val artists = track.optJSONArray("artists") ?: JSONArray()
                val artist = if (artists.length() > 0) artists.optJSONObject(0)?.optString("name").orEmpty() else ""
                apiRequest("PUT", "https://api.spotify.com/v1/me/player/play", token,
                    JSONObject().put("uris", JSONArray().put(trackUri)), allowNoContent = true)
                "Asked Spotify to play " + title + if (artist.isNotBlank()) " by " + artist else ""
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }
    }

    fun controlPlayback(context: Context, command: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val result = runCatching {
                val token = validAccessToken(context)
                val normalized = command.trim().lowercase()
                val endpoint: String
                val method: String
                when (normalized) {
                    "next" -> { endpoint = "https://api.spotify.com/v1/me/player/next"; method = "POST" }
                    "previous" -> { endpoint = "https://api.spotify.com/v1/me/player/previous"; method = "POST" }
                    "pause" -> { endpoint = "https://api.spotify.com/v1/me/player/pause"; method = "PUT" }
                    "resume", "play" -> { endpoint = "https://api.spotify.com/v1/me/player/play"; method = "PUT" }
                    "toggle" -> {
                        val current = apiRequest("GET", "https://api.spotify.com/v1/me/player", token)
                        val playing = current.optBoolean("is_playing", false)
                        endpoint = if (playing) "https://api.spotify.com/v1/me/player/pause" else "https://api.spotify.com/v1/me/player/play"
                        method = "PUT"
                    }
                    else -> throw IllegalArgumentException("Unsupported Spotify playback command.")
                }
                apiRequest(method, endpoint, token, allowNoContent = true)
                "Sent Spotify $normalized command."
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }
    }

    private fun validAccessToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(ACCESS_TOKEN, "").orEmpty()
        if (token.isNotBlank() && System.currentTimeMillis() < prefs.getLong(EXPIRES_AT, 0L) - 60_000L) return token
        val refresh = prefs.getString(REFRESH_TOKEN, "").orEmpty()
        if (refresh.isBlank()) throw IllegalStateException("Connect Spotify in Munch settings first.")
        val response = tokenRequest("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to getClientId(context))
        saveTokenResponse(context, response)
        return response.getString("access_token")
    }

    private fun saveTokenResponse(context: Context, json: JSONObject) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACCESS_TOKEN, json.getString("access_token"))
            .putLong(EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
            .putString("spotify_status", "Spotify connected")
        val refresh = json.optString("refresh_token")
        if (refresh.isNotBlank()) editor.putString(REFRESH_TOKEN, refresh)
        editor.apply()
    }

    private fun tokenRequest(vararg fields: Pair<String, String>): JSONObject {
        val connection = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 8000; readTimeout = 12000; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val body = fields.joinToString("&") {
                URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Spotify token endpoint HTTP " + code + ": " + raw.take(150))
            return JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun apiRequest(method: String, endpoint: String, token: String, body: JSONObject? = null, allowNoContent: Boolean = false): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 8000; readTimeout = 12000
            setRequestProperty("Authorization", "Bearer " + token); setRequestProperty("Content-Type", "application/json")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (allowNoContent && code in 200..299) return JSONObject()
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 404 && allowNoContent) throw IllegalStateException("Spotify has no active device. Open Spotify on your phone and start playback once.")
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
                throw IllegalStateException("Spotify API HTTP " + code + if (detail.isNotBlank()) ": " + detail else "")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally { connection.disconnect() }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val raw = ByteArray(bytes); random.nextBytes(raw)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
