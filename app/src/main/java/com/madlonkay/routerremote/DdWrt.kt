package com.madlonkay.routerremote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DdWrt"

suspend fun ddWrtVpnToggle(host: String, user: String, pass: String, enable: Boolean): HttpResult {
    val value = if (enable) "1" else "0"
    return ddWrtApplyuser(host, user, pass,
            mapOf("openvpncl_enable" to value,
                    "submit_button" to "PPTP",
                    "action" to "ApplyTake"))
}

suspend fun ddWrtApplyuser(host: String, user: String, pass: String, data: Map<String, String>): HttpResult = withContext(CommonPool) {
    val conn = setUpConnection("http://$host/applyuser.cgi", user, pass)
    conn.doOutput = true
    conn.requestMethod = "POST"
    Log.d(TAG, "Going to write out; data=$data")
    conn.outputStream.use { out ->
        data.entries.forEach { out.write("${it.key}=${it.value}&".toByteArray()) }
    }
    Log.d(TAG, "Wrote out; going to connect")
    conn.connect()
    Log.d(TAG, "Connected; response=${conn.responseCode}")
    return@withContext HttpResult(conn.responseCode, conn.responseMessage, conn.resultText)
}

suspend fun ddWrtStatusOpenVpn(host: String, user: String, pass: String): HttpResult = withContext(CommonPool) {
    val conn = setUpConnection("http://$host/Status_OpenVPN.asp", user, pass)
    Log.d(TAG, "Going to connect")
    conn.connect()
    Log.d(TAG, "Connected; response=${conn.responseCode}")
    return@withContext HttpResult(conn.responseCode, conn.responseMessage, conn.resultText)
}

private fun setUpConnection(host: String, user: String, pass: String): HttpURLConnection {
    val url = URL(host)
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.useCaches = false
    conn.setRequestProperty("Authorization", "Basic ${Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)}")
    return conn
}
