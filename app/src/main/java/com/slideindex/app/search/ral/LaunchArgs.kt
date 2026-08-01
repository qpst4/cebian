package com.slideindex.app.search.ral

import android.content.Intent
import android.content.IntentFilter

data class LaunchArgs(
    val intent: Intent,
    val filters: List<IntentFilter>,
)
