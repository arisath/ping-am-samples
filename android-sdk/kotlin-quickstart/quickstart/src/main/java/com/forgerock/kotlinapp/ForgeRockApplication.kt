package com.novapay.app

import android.app.Application
import org.forgerock.android.auth.FRAuth
import org.forgerock.android.auth.Logger

class NovapayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FRAuth.start(this)
        Logger.set(Logger.Level.DEBUG)
    }
}
