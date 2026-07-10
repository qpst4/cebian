package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.util.VolumeControlHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageEnvironmentPort @Inject constructor() : MessageEnvironmentPort {
    override fun isSystemDndEnabled(context: Context): Boolean =
        VolumeControlHelper.isDndEnabled(context)
}
