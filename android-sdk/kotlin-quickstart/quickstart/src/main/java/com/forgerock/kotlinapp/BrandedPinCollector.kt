/*
 * Copyright (c) 2022 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.forgerock.kotlinapp

import android.content.Context
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.forgerock.android.auth.callback.DeviceBindingAuthenticationType
import org.forgerock.android.auth.devicebind.ApplicationPinDeviceAuthenticator
import org.forgerock.android.auth.devicebind.BiometricAndDeviceCredential
import org.forgerock.android.auth.devicebind.BiometricOnly
import org.forgerock.android.auth.devicebind.DeviceAuthenticator
import org.forgerock.android.auth.devicebind.None
import org.forgerock.android.auth.devicebind.PinCollector
import org.forgerock.android.auth.devicebind.Prompt
import org.forgerock.android.auth.devicebind.UserKey
import java.security.PrivateKey
import java.security.Signature
import java.util.Date

class BrandedPinCollector : PinCollector {

    override suspend fun collectPin(prompt: Prompt, fragmentActivity: FragmentActivity): CharArray =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val existing = fragmentActivity.supportFragmentManager
                    .findFragmentByTag(BrandedPinFragment.TAG) as? BrandedPinFragment
                existing?.let {
                    it.continuation = continuation
                } ?: run {
                    BrandedPinFragment.newInstance(prompt, continuation)
                        .show(fragmentActivity.supportFragmentManager, BrandedPinFragment.TAG)
                }
            }
        }
}

/**
 * Subclasses ApplicationPinDeviceAuthenticator to intercept the signed JWS
 * so the app can capture it before it is sent to AM.
 */
class CapturingPinAuthenticator(
    pinCollector: PinCollector,
    private val onJwsSigned: (String) -> Unit
) : ApplicationPinDeviceAuthenticator(pinCollector) {

    override fun sign(
        context: Context,
        userKey: UserKey,
        privateKey: PrivateKey,
        signature: Signature?,
        challenge: String,
        expiration: Date,
        customClaims: Map<String, Any>
    ): String {
        val jws = super.sign(context, userKey, privateKey, signature, challenge, expiration, customClaims)
        onJwsSigned(jws)
        return jws
    }
}

fun brandedDeviceAuthenticator(onJwsSigned: ((String) -> Unit)? = null): (DeviceBindingAuthenticationType) -> DeviceAuthenticator = { type ->
    when (type) {
        DeviceBindingAuthenticationType.APPLICATION_PIN ->
            if (onJwsSigned != null) CapturingPinAuthenticator(BrandedPinCollector(), onJwsSigned)
            else ApplicationPinDeviceAuthenticator(BrandedPinCollector())
        DeviceBindingAuthenticationType.BIOMETRIC_ONLY -> BiometricOnly()
        DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK -> BiometricAndDeviceCredential()
        else -> None()
    }
}
