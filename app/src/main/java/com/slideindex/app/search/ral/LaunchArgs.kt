/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 */
package com.slideindex.app.search.ral

import android.content.Intent
import android.content.IntentFilter

data class LaunchArgs(
    val intent: Intent,
    val filters: List<IntentFilter>,
)
