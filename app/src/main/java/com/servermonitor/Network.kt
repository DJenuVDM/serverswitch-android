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
            Log.d("Network", "Screens response code: ${resp.code}")
            if (!resp.isSuccessful) {
                Log.d("Network", "Screens request failed")
                return emptyList()
            }
            val body = resp.body?.string() ?: ""
            Log.d("Network", "Screens response body: $body")
            val j = JSONObject(body)
            if (j.has("error")) {
                Log.d("Network", "Screens error: ${j.getString("error")}")
                return emptyList()
            }
            if (j.has("screens")) {
                val arr = j.getJSONArray("screens")
                val screens = (0 until arr.length()).map { arr.getString(it) }
                Log.d("Network", "Screens: $screens")
                return screens
            } else {
                Log.d("Network", "No screens key in response")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.d("Network", "Screens exception: ${e.message}")
            return emptyList()
        }
    }

    fun sendScreenCommand(device: Device, screenName: String, command: String): Boolean {
        return try {
            val encodedName = URLEncoder.encode(screenName, "UTF-8")
            val body = JSONObject().apply {
                put("command", command)
            }.toString().toRequestBody("application/json".toMediaType())
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
            Log.d("Network", "Screen log response code: ${resp.code}")
            if (!resp.isSuccessful) {
                Log.d("Network", "Screen log request failed")
                return null
            }
            val body = resp.body?.string() ?: ""
            Log.d("Network", "Screen log response body: $body")
            val j = JSONObject(body)
            if (j.has("error")) {
                Log.d("Network", "Screen log error: ${j.getString("error")}")
                return null
            } else {
                return j.optString("log", "")
            }
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
    fun pollAod(aod: Aod): String {
        return try {
            val req = Request.Builder().url("http://${aod.ip}:${aod.port}/ping").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) "on" else "off"
        } catch (e: Exception) { "off" }
    }

    fun wakeViaWol(aod: Aod, mac: String): Boolean {
        return try {
            val body = JSONObject().apply {
                put("mac", mac)
                put("broadcast", aod.broadcast)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("http://${aod.ip}:${aod.port}/wake/wol")
                .post(body).addHeader("X-Token", aod.token).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun wakeViaScript(aod: Aod, scriptName: String, args: List<String> = emptyList(), env: Map<String, String> = emptyMap()): Boolean = try {
        val body = JSONObject().apply {
            if (args.isNotEmpty()) put("args", org.json.JSONArray(args))
            if (env.isNotEmpty()) put("env", JSONObject(env))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://${aod.ip}:${aod.port}/wake/script/$scriptName")
            .post(body)
            .addHeader("X-Token", aod.token).build()
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
}