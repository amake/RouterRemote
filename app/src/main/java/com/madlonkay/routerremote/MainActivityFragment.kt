package com.madlonkay.routerremote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext

private const val TAG = "MainActivityFragment"
private const val REQUEST_CODE_LOCATION = 1

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment(), JobHolder {

    override val job = Job()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buttonVpnOn.onClick { doVpnToggle(true) }
        buttonVpnOff.onClick { doVpnToggle(false) }
        swipeRefreshLayout.onRefresh {
            updateVpnStatus()
            it.isRefreshing = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (!needsLocationPermission || hasLocationPermission) {
            launch { updateVpnStatus() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launch { updateVpnStatus() }
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
        textStatus.text = getString(R.string.message_thinking)
        if (dryRun) {
            Toast.makeText(context, R.string.toast_dry_run, Toast.LENGTH_SHORT).show()
            delay(500)
            Log.d(TAG, "Toggle dry run")
        } else {
            try {
                val result = ddWrtVpnToggle(host!!, user!!, pass!!, enable)
                Log.d(TAG, result.text)
                if (!result.succeeded) {
                    Toast.makeText(context, result.responseMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error trying to toggle OpenVPN", e)
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
        buttonVpnOn.isEnabled = true
        buttonVpnOff.isEnabled = true
        delay(500)
        updateVpnStatus()
    }

    private suspend fun updateVpnStatus() = withContext(UI) {
        textStatus.text = getString(R.string.message_thinking)
        val host = getPrefsString(R.string.key_host)
        val user = getPrefsString(R.string.key_username)
        val pass = getPrefsString(R.string.key_password)
        if (host.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
            textStatus.text = getString(R.string.message_unknown)
            Toast.makeText(context, R.string.toast_please_configure, Toast.LENGTH_SHORT).show()
            return@withContext
        }
        if (!checkNetwork()) {
            textStatus.text = getString(R.string.message_unknown)
            return@withContext
        }
        try {
            val result = ddWrtStatusOpenVpn(host!!, user!!, pass!!)
            Log.d(TAG, "VPN status: $result")
            if (result.succeeded) {
                val connected = result.text!!.contains(Regex("""CONNECTED\s+SUCCESS"""))
                val resId = if (connected) R.string.message_vpn_on else R.string.message_vpn_off
                textStatus.text = getString(resId)
            } else {
                textStatus.text = getString(R.string.message_error)
                Toast.makeText(context, result.responseMessage, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to get OpenVPN status", e)
            textStatus.text = getString(R.string.message_error)
            Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNetwork(): Boolean {
        if (needsLocationPermission && !hasLocationPermission) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_CODE_LOCATION)
            return false
        }
        if (!onAllowedNetwork) {
            val allowed = getPrefsString(R.string.key_allowed_network)
            val message = getString(R.string.toast_please_connect, allowed)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private val needsLocationPermission: Boolean
        get() = !getPrefsString(R.string.key_allowed_network).isNullOrBlank()

    private val hasLocationPermission: Boolean
        get() = context?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } ?: false

    private val onAllowedNetwork: Boolean
        get() {
            val allowed = getPrefsString(R.string.key_allowed_network)
            val current = currentNetwork
            // WifiInfo.getSSID() returns the name in quotes if it is valid UTF-8
            return allowed.isNullOrEmpty() || allowed == current || "\"$allowed\"" == current
        }

    private val currentNetwork: String?
        get() {
            val wifi = context?.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            return wifi?.connectionInfo?.ssid
        }
}
