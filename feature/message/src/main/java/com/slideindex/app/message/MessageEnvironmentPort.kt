package com.slideindex.app.message

import android.content.Context

interface MessageEnvironmentPort {
    fun isSystemDndEnabled(context: Context): Boolean
}
