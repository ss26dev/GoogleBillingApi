package com.softstackdev.googlebilling.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Created by Nena_Schmidt on 28.10.2020
 */

internal fun openPlayStore(context: Context, packageName: String) {
    try {
        val uriString = "market://details?id=$packageName"
        startIntent(context, Intent.ACTION_VIEW, uriString)
    } catch (e: ActivityNotFoundException) {
        val uriString = "http://play.google.com/store/apps/details?id=$packageName"
        startIntent(context, Intent.ACTION_VIEW, uriString)
    }
}

internal fun startIntent(context: Context, action: String, uriString: String) {
    val intent = Intent(action)
    intent.data = Uri.parse(uriString)
    context.startActivity(intent)
}

