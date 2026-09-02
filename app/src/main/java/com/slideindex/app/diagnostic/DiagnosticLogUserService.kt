package com.slideindex.app.diagnostic

import android.content.Context
import androidx.annotation.Keep

class DiagnosticLogUserService() : DiagnosticLogListenerService() {

    @Keep
    constructor(context: Context) : this()
}
