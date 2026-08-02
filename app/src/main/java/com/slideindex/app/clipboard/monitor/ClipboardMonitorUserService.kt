package com.slideindex.app.clipboard.monitor

import android.content.Context
import androidx.annotation.Keep

/** Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT). */
class ClipboardMonitorUserService() : ClipboardListenerService() {

    @Keep
    constructor(context: Context) : this()
}
