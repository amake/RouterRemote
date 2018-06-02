package com.madlonkay.routerremote

import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext

private const val TAG = "MainActivityFragment"

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
        buttonVpnOn.onClick { doVpnToggle(true, it) }
        buttonVpnOff.onClick { doVpnToggle(false, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onResume() {
        super.onResume()
        launch { updateVpnStatus() }
    }

    private suspend fun doVpnToggle(enable: Boolean, button: View) {
        val host = getPrefsString(R.string.key_host)!!
        val user = getPrefsString(R.string.key_username)!!
        val pass = getPrefsString(R.string.key_password)!!
        button.isEnabled = false
        val result = ddWrtVpnToggle(host, user, pass, enable)
        Log.d(TAG, result)
        button.isEnabled = true
        delay(500)
        updateVpnStatus()
    }

    private suspend fun updateVpnStatus() = withContext(UI) {
        val host = getPrefsString(R.string.key_host)!!
        val user = getPrefsString(R.string.key_username)!!
        val pass = getPrefsString(R.string.key_password)!!
        val status = ddWrtStatusOpenVpn(host, user, pass)
        Log.d(TAG, "VPN status: $status")
        val connected = status != null && status.contains(Regex("""CONNECTED\s+SUCCESS"""))
        val resId = if (connected) R.string.message_vpn_on else R.string.message_vpn_off
        textStatus.text = getString(resId)
    }
}
