package com.madlonkay.routerremote

import android.content.Context
import android.preference.PreferenceManager
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Context.getPrefsString(@StringRes resId: Int, defValue: String? = null): String? {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val key = getString(resId)
    return prefs.getString(key, defValue)
}

fun Context.getPrefsBoolean(@StringRes resId: Int, defValue: Boolean = false): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val key = getString(resId)
    return prefs.getBoolean(key, defValue)
}

fun Fragment.getPrefsString(@StringRes resId: Int, defValue: String? = null): String? {
    return context?.getPrefsString(resId, defValue)
}

fun Fragment.getPrefsBoolean(@StringRes resId: Int, defValue: Boolean = false): Boolean {
    return context?.getPrefsBoolean(resId, defValue) ?: defValue
}