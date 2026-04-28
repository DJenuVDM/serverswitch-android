package com.servermonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Device(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 5050,
    val token: String,
    val mac: String = "",                      // MAC address for Wake-on-LAN (optional)
    var lastStatus: String = "unknown",
    // Post-shutdown script: script name on the AOD to run after shutdown
    val postShutdownScript: String = "",       // script name (empty = none)
    val postShutdownDelaySeconds: Int = 30     // how long to wait after shutdown before running it
)

data class Aod(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 5051,
    val token: String,
    val broadcast: String = "255.255.255.255",
    val deviceIds: MutableList<String> = mutableListOf(),
    var lastStatus: String = "unknown"
)

object DeviceStore {
    private const val PREFS = "ServerSwitchPrefs"
    private const val KEY_DEVICES = "devices"
    private const val KEY_AODS = "aods"

    fun getDevices(context: Context): MutableList<Device> {
        val json = prefs(context).getString(KEY_DEVICES, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Device(
                id = o.getString("id"),
                name = o.getString("name"),
                ip = o.getString("ip"),
                port = o.optInt("port", 5050),
                token = o.getString("token"),
                mac = o.optString("mac", ""),
                lastStatus = o.optString("lastStatus", "unknown"),
                postShutdownScript = o.optString("postShutdownScript", ""),
                postShutdownDelaySeconds = o.optInt("postShutdownDelaySeconds", 30)
            )
        }.toMutableList()
    }

    fun saveDevices(context: Context, devices: List<Device>) {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id); put("name", d.name); put("ip", d.ip)
                put("port", d.port); put("token", d.token); put("mac", d.mac)
                put("lastStatus", d.lastStatus)
                put("postShutdownScript", d.postShutdownScript)
                put("postShutdownDelaySeconds", d.postShutdownDelaySeconds)
            })
        }
        prefs(context).edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    fun updateDeviceStatus(context: Context, id: String, status: String) {
        val devices = getDevices(context)
        val idx = devices.indexOfFirst { it.id == id }
        if (idx >= 0) { devices[idx] = devices[idx].copy(lastStatus = status); saveDevices(context, devices) }
    }

    fun getAods(context: Context): MutableList<Aod> {
        val json = prefs(context).getString(KEY_AODS, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val ids = o.optJSONArray("deviceIds") ?: JSONArray()
            Aod(
                id = o.getString("id"), name = o.getString("name"),
                ip = o.getString("ip"), port = o.optInt("port", 5051),
                token = o.getString("token"),
                broadcast = o.optString("broadcast", "255.255.255.255"),
                deviceIds = (0 until ids.length()).map { i -> ids.getString(i) }.toMutableList(),
                lastStatus = o.optString("lastStatus", "unknown")
            )
        }.toMutableList()
    }

    fun saveAods(context: Context, aods: List<Aod>) {
        val arr = JSONArray()
        aods.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("name", a.name); put("ip", a.ip)
                put("port", a.port); put("token", a.token); put("broadcast", a.broadcast)
                put("lastStatus", a.lastStatus)
                val ids = JSONArray(); a.deviceIds.forEach { ids.put(it) }; put("deviceIds", ids)
            })
        }
        prefs(context).edit().putString(KEY_AODS, arr.toString()).apply()
    }

    fun updateAodStatus(context: Context, id: String, status: String) {
        val aods = getAods(context)
        val idx = aods.indexOfFirst { it.id == id }
        if (idx >= 0) { aods[idx] = aods[idx].copy(lastStatus = status); saveAods(context, aods) }
    }

    fun getAodForDevice(context: Context, deviceId: String): Aod? =
        getAods(context).firstOrNull { it.deviceIds.contains(deviceId) }

    fun newId() = System.currentTimeMillis().toString()
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}