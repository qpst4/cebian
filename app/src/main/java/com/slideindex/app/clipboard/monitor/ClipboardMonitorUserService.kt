package com.slideindex.app.clipboard.monitor

import android.content.Context
import androidx.annotation.Keep

class ClipboardMonitorUserService() : ClipboardListenerService() {

    @Keep
    constructor(context: Context) : this()
}
