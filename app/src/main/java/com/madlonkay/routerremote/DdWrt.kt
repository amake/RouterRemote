package com.madlonkay.routerremote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DdWrt"

suspend fun ddWrtVpnToggle(host: String, user: String, pass: String, enable: Boolean): String? {
    val value = if (enable) "1" else "0"
    return ddWrtApplyuser(host, user, pass, mapOf("openvpncl_enable" to value))
}

suspend fun ddWrtApplyuser(host: String, user: String, pass: String, data: Map<String, String>): String? = withContext(CommonPool) {
    val conn = setUpConnection("http://$host/applyuser.cgi", user, pass)
    conn.doOutput = true
    conn.requestMethod = "POST"
    Log.d(TAG, "Going to write out; data=$data")
    conn.outputStream.use { out ->
        out.write("action=Apply".toByteArray())
        data.entries.forEach { out.write("&${it.key}=${it.value}".toByteArray()) }
    }
    Log.d(TAG, "Wrote out; going to connect")
    conn.connect()
    Log.d(TAG, "Connected; response=${conn.responseCode}")
    return@withContext if (conn.responseCode == HttpURLConnection.HTTP_OK) {
        conn.inputStream
    } else {
        conn.errorStream
    }.bufferedReader().use { it.readText() }
}

suspend fun ddWrtStatusOpenVpn(host: String, user: String, pass: String): String? = withContext(CommonPool) {
    val conn = setUpConnection("http://$host/Status_OpenVPN.asp", user, pass)
    Log.d(TAG, "Going to connect")
    conn.connect()
    Log.d(TAG, "Connected; response=${conn.responseCode}")
    return@withContext if (conn.responseCode == HttpURLConnection.HTTP_OK) {
        conn.inputStream
    } else {
        conn.errorStream
    }.bufferedReader().use { it.readText() }
}

private fun setUpConnection(host: String, user: String, pass: String): HttpURLConnection {
    val url = URL(host)
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.useCaches = false
    conn.setRequestProperty("Authorization", "Basic ${Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)}")
    return conn
}
