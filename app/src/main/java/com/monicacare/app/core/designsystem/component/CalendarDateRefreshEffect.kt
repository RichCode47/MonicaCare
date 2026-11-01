package com.monicacare.app.core.designsystem.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Refresh on resume and calendar/clock changes; no timer or background polling. */
@Composable
fun CalendarDateRefreshEffect(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val refresh = rememberUpdatedState(onRefresh)
    LifecycleResumeEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh.value() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh.value()
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
}
