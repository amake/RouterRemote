package com.madlonkay.routerremote

import android.content.Context
import android.preference.PreferenceManager
import android.support.annotation.StringRes
import android.support.v4.app.Fragment

fun Context.getPrefsString(@StringRes resId: Int, defValue: String? = null): String? {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val key = getString(resId)
    return prefs.getString(key, defValue)
}

fun Fragment.getPrefsString(@StringRes resId: Int, defValue: String? = null): String? {
    return context?.getPrefsString(resId, defValue)
}