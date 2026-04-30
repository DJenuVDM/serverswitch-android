package com.servermonitor

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ServerInfo(
    val cpuPercent: Double, val ramPercent: Double,
    val ramUsedGb: Double, val ramTotalGb: Double,
    val diskPercent: Double, val diskUsedGb: Double,
    val diskTotalGb: Double, val uptimeSeconds: Long
)

/** A result that carries either a success value or a human-readable error message. */
sealed class NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()

    val isSuccess get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): String? = (this as? Error)?.message
}

/** Pull a friendly message out of a JSON error body, falling back to a raw description. */
private fun friendlyError(code: Int, body: String?, context: String, deviceName: String = ""): String {
    val device = if (deviceName.isNotEmpty()) deviceName else "the server"

    // Try to parse a structured error from the response body first
    if (body != null) {
        try {
            val j = JSONObject(body)
            val err = j.optString("error", "")
            val detail = j.optString("details", "")

            if (err.isNotEmpty()) {
                val explanation = when (err) {

                    "unauthorized" ->
                        "The auth token was rejected by $device.\n\n" +
                        "The token in the app does not match the one set on the server. " +
                        "Open the device settings in the app and make sure the token matches " +
                        "what is in /opt/serverswitch/config.env on the server."

                    "rate_limited" ->
                        "Too many requests sent to $device in a short time.\n\n" +
                        "The server is temporarily ignoring requests from this app to protect itself. " +
                        "Wait 60 seconds and try again."

                    "screen_not_installed" ->
                        "The 'screen' program is not installed on $device.\n\n" +
                        "Screen sessions require the 'screen' utility. " +
                        "SSH into the server and run:\n  sudo apt install screen\n\n" +
                        "After installing, try again — no restart of the ServerSwitch service is needed."

                    "script_not_found" ->
                        "The script was not found on $device, or it is not marked as executable.\n\n" +
                        "Make sure the script exists in /opt/serverswitch/scripts/ and has execute " +
                        "permission. On the server, run:\n  sudo chmod +x /opt/serverswitch/scripts/<scriptname>"

                    "invalid_script_name" ->
                        "The script name contained characters the server won't accept.\n\n" +
                        "Script names cannot contain slashes or '..'  — they must be a plain filename, " +
                        "not a path. Check that the script name shown in the list is correct."

                    "invalid_screen_name" ->
                        "The screen session name you entered is not valid.\n\n" +
                        "Session names can only contain letters (a–z, A–Z), numbers, hyphens and " +
                        "underscores. Remove any spaces or special characters and try again."

                    "invalid_args" ->
                        "The arguments could not be accepted by $device.\n\n" +
                        "Arguments must be plain text strings. Each one must be under 500 characters, " +
                        "and you cannot pass more than 64 arguments at once."

                    "too_many_args" ->
                        "Too many arguments were passed to the script.\n\n" +
                        "The server accepts a maximum of 64 arguments per script run. " +
                        "Reduce the number of arguments and try again."

                    "arg_too_long" ->
                        "One of the arguments is too long.\n\n" +
                        "Each individual argument must be under 500 characters."

                    "command_failed" ->
                        "The command was sent but the server's screen utility reported a failure.\n\n" +
                        "This usually means the screen session has ended or the session name is wrong. " +
                        "Go back to the Screens tab to check whether the session is still running."

                    "missing_command" ->
                        "No command text was included in the request.\n\n" +
                        "This is likely a bug in the app. Try again, and if it keeps happening " +
                        "please report it."

                    "invalid_command" ->
                        "The command was rejected by $device.\n\n" +
                        "Commands must be plain text and under 1000 characters. Tab characters " +
                        "are not allowed."

                    "screen_log_failed" ->
                        "Could not read the log for this screen session.\n\n" +
                        "The session may have ended since you opened this screen. " +
                        "Go back to the Screens tab and refresh — if the session is gone, " +
                        "the script has finished running."

                    "screen_list_failed" ->
                        "Could not get the list of screen sessions from $device.\n\n" +
                        "The server ran into an error calling the 'screen' command. " +
                        "SSH into the server and run 'screen -ls' to check if screen is working correctly."

                    "run_failed" ->
                        "The server tried to start the script but something went wrong.\n\n" +
                        "Check the server log for details:\n  tail -f /opt/serverswitch/serverswitch.log" +
                        if (detail.isNotEmpty()) "\n\nServer said: $detail" else ""

                    "invalid_offset" ->
                        "The log offset sent by the app was not a valid number.\n\n" +
                        "This is a bug in the app. Try closing and reopening the log view."

                    else ->
                        "The server returned an unexpected error: '$err'" +
                        if (detail.isNotEmpty()) "\n\nDetail: $detail" else ""
                }

                // For run_failed the detail is already embedded above
                return if (detail.isNotEmpty() && err != "run_failed") {
                    "$explanation\n\nServer detail: $detail"
                } else {
                    explanation
                }
            }
        } catch (_: Exception) {
            // Body was not JSON — fall through to HTTP code handling below
        }
    }

    // No structured error body — describe the HTTP status code itself
    return when (code) {
        401 ->
            "Authentication failed — $device rejected the request as unauthorized.\n\n" +
            "The token in the app does not match the server. Open the device settings " +
            "and check the auth token matches /opt/serverswitch/config.env on the server."

        403 ->
            "Access forbidden — $device refused the request.\n\n" +
            "This is unexpected. Check that the server is running the correct version."

        404 ->
            "The requested resource was not found on $device.\n\n" +
            "This can happen if the server is running an older version that does not " +
            "support this feature yet. Run sudo bash update.sh on the server to update."

        429 ->
            "Rate limit hit — $device is temporarily ignoring requests.\n\n" +
            "Too many requests were sent in a short window. Wait 60 seconds and try again."

        500 ->
            "$device encountered an internal error.\n\n" +
            "Something went wrong on the server side. Check the server log for details:\n" +
            "  tail -f /opt/serverswitch/serverswitch.log"

        502, 503, 504 ->
            "$device or a gateway in front of it is not responding correctly (HTTP $code).\n\n" +
            "The ServerSwitch service may be starting up or have crashed. " +
            "SSH in and check: sudo systemctl status serverswitch"

        0 ->
            // Code 0 = connection-level failure, already handled at the call site
            context

        else ->
            "$device returned an unexpected HTTP $code response while trying to: $context.\n\n" +
            "Check the server log for more information:\n" +
            "  tail -f /opt/serverswitch/serverswitch.log"
    }
}

object Network {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // ── Device ────────────────────────────────────────────────────────────────
    fun pollDevice(device: Device): String = try {
        val req = Request.Builder().url("http://${device.ip}:${device.port}/ping").build()
        if (client.newCall(req).execute().isSuccessful) "on" else "off"
    } catch (e: Exception) { "off" }

    fun sendShutdown(device: Device): Boolean = try {
        val req = Request.Builder().url("http://${device.ip}:${device.port}/shutdown")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("X-Token", device.token).build()
        client.newCall(req).execute().isSuccessful
    } catch (e: Exception) { false }

    fun sendReboot(device: Device): Boolean = try {
        val req = Request.Builder().url("http://${device.ip}:${device.port}/reboot")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("X-Token", device.token).build()
        client.newCall(req).execute().isSuccessful
    } catch (e: Exception) { false }

    fun getInfo(device: Device): ServerInfo? {
        return try {
            val req = Request.Builder().url("http://${device.ip}:${device.port}/info")
                .addHeader("X-Token", device.token).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) null else {
                val j = JSONObject(resp.body?.string() ?: return null)
                if (j.has("error")) null else ServerInfo(
                    j.getDouble("cpu_percent"), j.getDouble("ram_percent"),
                    j.getDouble("ram_used_gb"), j.getDouble("ram_total_gb"),
                    j.getDouble("disk_percent"), j.getDouble("disk_used_gb"),
                    j.getDouble("disk_total_gb"), j.getLong("uptime_seconds")
                )
            }
        } catch (e: Exception) { null }
    }

    fun listScreens(device: Device): List<String> {
        return try {
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}/screens")
                .addHeader("X-Token", device.token)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: ""
            val j = JSONObject(body)
            if (j.has("screens")) {
                val arr = j.getJSONArray("screens")
                (0 until arr.length()).map { arr.getString(it) }
            } else emptyList()
        } catch (e: Exception) {
            Log.d("Network", "listScreens exception: ${e.message}")
            emptyList()
        }
    }

    fun sendScreenCommand(device: Device, screenName: String, command: String): Boolean {
        return try {
            val encodedName = URLEncoder.encode(screenName, "UTF-8")
            val body = JSONObject().apply { put("command", command) }
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}/screens/$encodedName/command")
                .post(body)
                .addHeader("X-Token", device.token)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) {
            Log.d("Network", "Screen command exception: ${e.message}")
            false
        }
    }

    fun getScreenLog(device: Device, screenName: String): String? {
        return try {
            val encodedName = URLEncoder.encode(screenName, "UTF-8")
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}/screens/$encodedName/log")
                .addHeader("X-Token", device.token)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: ""
            val j = JSONObject(body)
            if (j.has("error")) null else j.optString("log", "")
        } catch (e: Exception) {
            Log.d("Network", "Screen log exception: ${e.message}")
            null
        }
    }

    fun tailScreenLog(device: Device, screenName: String, offset: Int): Pair<List<String>, Int>? {
        return try {
            val encodedName = URLEncoder.encode(screenName, "UTF-8")
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}/screens/$encodedName/log/tail?offset=$offset")
                .addHeader("X-Token", device.token)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val j = JSONObject(resp.body?.string() ?: return null)
            if (j.has("error")) return null
            val arr = j.getJSONArray("new_lines")
            val lines = (0 until arr.length()).map { arr.getString(it) }
            Pair(lines, j.getInt("next_offset"))
        } catch (e: Exception) {
            Log.d("Network", "Screen tail exception: ${e.message}")
            null
        }
    }

    // ── AOD ───────────────────────────────────────────────────────────────────
    fun pollAod(aod: Aod): String = try {
        val req = Request.Builder().url("http://${aod.ip}:${aod.port}/ping").build()
        if (client.newCall(req).execute().isSuccessful) "on" else "off"
    } catch (e: Exception) { "off" }

    fun wakeViaWol(aod: Aod, mac: String): Boolean = try {
        val body = JSONObject().apply {
            put("mac", mac); put("broadcast", aod.broadcast)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("http://${aod.ip}:${aod.port}/wake/wol")
            .post(body).addHeader("X-Token", aod.token).build()
        client.newCall(req).execute().isSuccessful
    } catch (e: Exception) { false }

    fun wakeViaScript(aod: Aod, scriptName: String, args: List<String> = emptyList(), env: Map<String, String> = emptyMap()): Boolean = try {
        val body = JSONObject().apply {
            if (args.isNotEmpty()) put("args", JSONArray(args))
            if (env.isNotEmpty()) put("env", JSONObject(env))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://${aod.ip}:${aod.port}/wake/script/$scriptName")
            .post(body).addHeader("X-Token", aod.token).build()
        client.newCall(req).execute().isSuccessful
    } catch (e: Exception) { false }

    fun listScripts(aod: Aod): List<String> = try {
        val req = Request.Builder().url("http://${aod.ip}:${aod.port}/scripts")
            .addHeader("X-Token", aod.token).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) emptyList() else {
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("scripts") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        }
    } catch (e: Exception) { emptyList() }

    // ── Device-side script listing & execution ────────────────────────────────

    fun listScripts(device: Device): List<String> = try {
        val req = Request.Builder().url("http://${device.ip}:${device.port}/scripts")
            .addHeader("X-Token", device.token).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) emptyList() else {
            val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("scripts") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        }
    } catch (e: Exception) { emptyList() }

    /**
     * Run a named script on a device.
     * Returns a NetworkResult so callers get a human-readable error on failure
     * instead of a silent false.
     */
    fun runScript(
        device: Device,
        scriptName: String,
        args: List<String> = emptyList(),
        screenName: String = ""
    ): NetworkResult<String> {
        return try {
            val body = JSONObject().apply {
                if (args.isNotEmpty()) put("args", JSONArray(args))
                if (screenName.isNotEmpty()) put("screen_name", screenName)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://${device.ip}:${device.port}/scripts/run/$scriptName")
                .post(body)
                .addHeader("X-Token", device.token)
                .build()
            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string()
            if (resp.isSuccessful) {
                NetworkResult.Success(scriptName)
            } else {
                NetworkResult.Error(friendlyError(resp.code, bodyStr, "run script $scriptName", device.name))
            }
        } catch (e: Exception) {
            Log.d("Network", "runScript exception: ${e.message}")
            val msg = when {
                e.message?.contains("failed to connect", ignoreCase = true) == true ||
                e.message?.contains("connection refused", ignoreCase = true) == true ->
                    "Could not connect to ${device.name} at ${device.ip}:${device.port}.\n\n" +
                    "The server is either offline, unreachable on this network, or the ServerSwitch " +
                    "service is not running. SSH in and check:\n  sudo systemctl status serverswitch"
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Connection to ${device.name} timed out.\n\n" +
                    "The server took too long to respond. This usually means the device is offline " +
                    "or you are not connected to the same network (Tailscale). " +
                    "Check that Tailscale is running on both devices."
                e.message?.contains("unable to resolve", ignoreCase = true) == true ||
                e.message?.contains("no address", ignoreCase = true) == true ->
                    "Could not resolve the address for ${device.name} (${device.ip}).\n\n" +
                    "The IP address could not be reached. Check that Tailscale is connected " +
                    "and that the IP in the app matches the device's Tailscale IP."
                else ->
                    "A network error occurred while trying to reach ${device.name}.\n\n" +
                    "Error detail: ${e.message}"
            }
            NetworkResult.Error(msg)
        }
    }
}