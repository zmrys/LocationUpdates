package com.jacknephilim.locationupdates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

fun Context.servicePendingIntent(intent: Intent, flag: Int) =
    PendingIntent.getService(this, 0, intent, flag)

fun Context.activityPendingIntent(cls: Class<*>?, flag: Int) =
    PendingIntent.getActivity(
        this, 0,
        Intent(this, cls), flag
    )

fun Context.createIntent(cls: Class<*>?) =
    Intent(this, cls)



