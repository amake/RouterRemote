package com.madlonkay.routerremote

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main
import kotlinx.coroutines.android.UI

private const val TAG = "MainActivityFragment"
private const val REQUEST_CODE_LOCATION = 1

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment(), JobHolder {

    override val job = Job()

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (onWifi) {
                Log.d(TAG, "Connected to Wi-Fi")
                GlobalScope.launch { updateVpnStatus() }
            } else {
                Log.d(TAG, "Not connected to Wi-Fi")
                textStatus.setText(R.string.message_unknown)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buttonVpnOn.onClick { doVpnToggle(true) }
        buttonVpnOff.onClick { doVpnToggle(false) }
        progressBarOn.visibility = View.INVISIBLE
        progressBarOff.visibility = View.INVISIBLE
        swipeRefreshLayout.onRefresh {
            updateVpnStatus()
            it.isRefreshing = false
        }
        context?.apply {
            val intentFilter = IntentFilter().apply { addAction(ConnectivityManager.CONNECTIVITY_ACTION) }
            registerReceiver(networkReceiver, intentFilter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        context?.unregisterReceiver(networkReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (!ssidIsRestricted || hasLocationPermission) {
            GlobalScope.launch { updateVpnStatus() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GlobalScope.launch { updateVpnStatus() }
            } else {
                Toast.makeText(context, R.string.toast_location_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun doVpnToggle(enable: Boolean) {
        val host = getPrefsString(R.string.key_host)
        val user = getPrefsString(R.string.key_username)
        val pass = getPrefsString(R.string.key_password)
        if (host.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
            Toast.makeText(context, R.string.toast_please_configure, Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkNetwork()) {
            return
        }
        val dryRun = getPrefsBoolean(R.string.key_dry_run)
        buttonVpnOn.isEnabled = false
        buttonVpnOff.isEnabled = false
        val progressBar = if (enable) progressBarOn else progressBarOff
        progressBar.visibility = View.VISIBLE
        textStatus.text = getString(R.string.message_thinking)
        if (dryRun) {
            Toast.makeText(context, R.string.toast_dry_run, Toast.LENGTH_SHORT).show()
            delay(500)
            Log.d(TAG, "Toggle dry run")
        } else {
            try {
                val result = ddWrtVpnToggle(host, user, pass, enable)
                Log.d(TAG, result.text)
                if (!result.succeeded) {
                    Toast.makeText(context, result.responseMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error trying to toggle OpenVPN", e)
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
        val delayTime = if (enable) 1500L else 500L
        for (i in 1..5) {
            delay(delayTime)
            val result = updateVpnStatus()
            if (result == UpdateResult.ERROR
                    || (enable && result == UpdateResult.ON)
                    || (!enable && result == UpdateResult.OFF)) {
                break
            }
        }
        buttonVpnOn.isEnabled = true
        buttonVpnOff.isEnabled = true
        progressBar.visibility = View.INVISIBLE
    }

    enum class UpdateResult {
        ON, OFF, ERROR, UNKNOWN
    }

    private suspend fun updateVpnStatus(): UpdateResult = withContext(Dispatchers.Main) {
        textStatus.text = getString(R.string.message_thinking)
        val host = getPrefsString(R.string.key_host)
        val user = getPrefsString(R.string.key_username)
        val pass = getPrefsString(R.string.key_password)
        if (host.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
            textStatus.text = getString(R.string.message_unknown)
            Toast.makeText(context, R.string.toast_please_configure, Toast.LENGTH_SHORT).show()
            return@withContext UpdateResult.UNKNOWN
        }
        if (!checkNetwork()) {
            textStatus.text = getString(R.string.message_unknown)
            return@withContext UpdateResult.UNKNOWN
        }
        var ret: UpdateResult
        try {
            val result = ddWrtStatusOpenVpn(host, user, pass)
            Log.d(TAG, "VPN status: $result")
            if (result.succeeded) {
                val connected = result.text!!.contains(Regex("""CONNECTED\s+SUCCESS"""))
                val resId = if (connected) R.string.message_vpn_on else R.string.message_vpn_off
                textStatus.text = getString(resId)
                ret = if (connected) UpdateResult.ON else UpdateResult.OFF
            } else {
                textStatus.text = getString(R.string.message_error)
                Toast.makeText(context, result.responseMessage, Toast.LENGTH_SHORT).show()
                ret = UpdateResult.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to get OpenVPN status", e)
            textStatus.text = getString(R.string.message_error)
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            ret = UpdateResult.ERROR
        }
        return@withContext ret
    }

    private fun checkNetwork(): Boolean {
        if (!onWifi) {
            Log.d(TAG, "Not on Wi-Fi")
            val message = if (ssidIsRestricted) {
                val allowed = getPrefsString(R.string.key_allowed_network)
                getString(R.string.toast_please_connect, allowed)
            } else {
                getString(R.string.toast_please_connect_wifi)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return false
        }
        if (ssidIsRestricted && !hasLocationPermission) {
            Log.d(TAG, "Location permissions required but not yet obtained")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_CODE_LOCATION)
            return false
        }
        if (!onAllowedNetwork) {
            val allowed = getPrefsString(R.string.key_allowed_network)
            Log.d(TAG, "Not connected to required Wi-Fi network matching '$allowed'")
            val message = getString(R.string.toast_please_connect, allowed)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private val ssidIsRestricted: Boolean
        get() = !getPrefsString(R.string.key_allowed_network).isNullOrBlank()

    private val hasLocationPermission: Boolean
        get() = context?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } ?: false

    private val onWifi: Boolean
        get() {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            return cm?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }

    private val onAllowedNetwork: Boolean
        get() {
            val allowed = getPrefsString(R.string.key_allowed_network)
            if (allowed.isNullOrEmpty()) {
                return true
            }
            val current = currentNetwork
            if (current.isNullOrBlank()) {
                return false
            }
            // WifiInfo.getSSID() returns the name in quotes if it is valid UTF-8
            return Regex(allowed).matches(current.unwrap('"'))
        }

    private val currentNetwork: String?
        get() {
            val wifi = context?.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            return wifi?.connectionInfo?.ssid
        }
}
