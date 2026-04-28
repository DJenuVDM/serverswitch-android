package com.servermonitor

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    fun getInfo(device: Device): ServerInfo? = try {
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

    // ── AOD ───────────────────────────────────────────────────────────────────
    fun pollAod(aod: Aod): String = try {
        val req = Request.Builder().url("http://${aod.ip}:${aod.port}/ping").build()
        val resp = client.newCall(req).execute()
        if (resp.isSuccessful) "on" else "off"
    } catch (e: Exception) { "off" }

    fun wakeViaWol(aod: Aod, mac: String): Boolean = try {
        val body = JSONObject().apply {
            put("mac", mac)
            put("broadcast", aod.broadcast)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("http://${aod.ip}:${aod.port}/wake/wol")
            .post(body).addHeader("X-Token", aod.token).build()
        client.newCall(req).execute().isSuccessful
    } catch (e: Exception) { false }

    fun wakeViaScript(aod: Aod, scriptName: String): Boolean = try {
        val req = Request.Builder()
            .url("http://${aod.ip}:${aod.port}/wake/script/$scriptName")
            .post("{}".toRequestBody("application/json".toMediaType()))
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
