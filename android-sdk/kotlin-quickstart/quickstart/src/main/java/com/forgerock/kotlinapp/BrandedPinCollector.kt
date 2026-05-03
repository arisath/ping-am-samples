/*
 * Copyright (c) 2022 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.forgerock.kotlinapp

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

fun brandedDeviceAuthenticator(): (DeviceBindingAuthenticationType) -> DeviceAuthenticator = { type ->
    when (type) {
        DeviceBindingAuthenticationType.APPLICATION_PIN -> ApplicationPinDeviceAuthenticator(BrandedPinCollector())
        DeviceBindingAuthenticationType.BIOMETRIC_ONLY -> BiometricOnly()
        DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK -> BiometricAndDeviceCredential()
        else -> None()
    }
}
