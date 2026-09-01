package com.slideindex.app.freezer

import android.content.Context
import android.content.Intent
import com.slideindex.app.MainActivity

object FreezerPanelIntents {
    fun openPanel(context: Context): Intent =
        Intent(context, FreezerPanelActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun manageApps(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_NAV_ROUTE, MainActivity.NAV_ROUTE_EXTENSION_FREEZER_APPS)
        }
}
