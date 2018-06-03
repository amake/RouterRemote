package com.madlonkay.routerremote

import java.net.HttpURLConnection

val HttpURLConnection.succeeded: Boolean
    get() = responseCode == HttpURLConnection.HTTP_OK

val HttpURLConnection.resultText: String?
    get() = if (succeeded) {
        inputStream
    } else {
        errorStream
    }?.bufferedReader()?.use { it.readText() }

data class HttpResult(val responseCode: Int, val responseMessage: String, val text: String?) {
    val succeeded: Boolean
        get() = responseCode == HttpURLConnection.HTTP_OK
}