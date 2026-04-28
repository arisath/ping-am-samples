/*
 * Copyright (c) 2022 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.forgerock.kotlinapp

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.forgerock.android.auth.FRListener
import org.forgerock.android.auth.FRSession
import org.forgerock.android.auth.FRUser
import org.forgerock.android.auth.Logger
import org.forgerock.android.auth.Node
import org.forgerock.android.auth.NodeListener
import org.forgerock.android.auth.callback.DeviceBindingCallback
import org.forgerock.android.auth.callback.DeviceProfileCallback
import org.forgerock.android.auth.callback.DeviceSigningVerifierCallback
import org.forgerock.android.auth.callback.IdPCallback
import org.forgerock.android.auth.callback.SelectIdPCallback
import org.forgerock.android.auth.callback.WebAuthnAuthenticationCallback
import org.forgerock.android.auth.callback.WebAuthnRegistrationCallback

interface ActivityListener {
    fun logout()
}

class MainActivity : AppCompatActivity(), ActivityListener {

    private val status: TextView by lazy { findViewById(R.id.status) }
    private val btnBindDevice: Button by lazy { findViewById(R.id.btn_bind_device) }
    private val btnLoginBiometrics: Button by lazy { findViewById(R.id.btn_login_biometrics) }
    private val btnCheckApprovals: Button by lazy { findViewById(R.id.btn_check_approvals) }
    private val btnLogout: Button by lazy { findViewById(R.id.btn_logout) }
    private val tag = MainActivity::class.java.name

    // Held so NodeDialogFragment / ChoiceDialogFragment can call node.next()
    internal var currentNodeListener: NodeListener<FRSession>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnBindDevice.setOnClickListener { startDeviceBinding() }
        btnLoginBiometrics.setOnClickListener { startDeviceAuth() }
        btnCheckApprovals.setOnClickListener { checkApprovals() }
        btnLogout.setOnClickListener { logout() }
    }

    override fun onStart() {
        super.onStart()
        val existing = supportFragmentManager.findFragmentByTag(UserInfoFragment.TAG) as? UserInfoFragment
        existing?.let { supportFragmentManager.beginTransaction().remove(it).commit() }
        updateLogoutVisibility()
        showStatus(if (FRUser.getCurrentUser() != null) "Authenticated" else "Not authenticated")
    }

    // ── Device Binding ────────────────────────────────────────────────────────

    private fun startDeviceBinding() {
        val journeyName = getString(R.string.forgerock_device_binding_service)
        showStatus("Starting device binding…")
        FRSession.authenticate(this, journeyName, object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                runOnUiThread { showStatus("Device bound successfully") }
            }

            override fun onException(e: Exception) {
                Logger.error(tag, e.message, e)
                showError("Device binding failed: ${e.message}")
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onCallbackReceived(node: Node) {
                handleNode(node, this)
            }
        })
    }

    // ── Device Auth (PIN / Biometrics) ────────────────────────────────────────

    private fun startDeviceAuth() {
        val journeyName = getString(R.string.forgerock_device_auth_service)
        showStatus("Authenticating…")
        FRSession.authenticate(this, journeyName, object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                runOnUiThread {
                    updateLogoutVisibility()
                    showStatus("Authenticated")
                    hideMainButtons()
                    FRUser.getCurrentUser()?.getAccessToken(object : FRListener<org.forgerock.android.auth.AccessToken> {
                        override fun onSuccess(token: org.forgerock.android.auth.AccessToken) {
                            runOnUiThread {
                                val fragment = UserInfoFragment.newInstance(
                                    token.value,
                                    token.refreshToken ?: "",
                                    token.idToken ?: "",
                                    this@MainActivity
                                )
                                supportFragmentManager.beginTransaction()
                                    .add(R.id.container, fragment, UserInfoFragment.TAG)
                                    .commit()
                            }
                        }

                        override fun onException(e: Exception) {
                            Logger.error(tag, e.message, e)
                        }
                    })
                }
            }

            override fun onException(e: Exception) {
                Logger.error(tag, e.message, e)
                showError("Login failed: ${e.message}")
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onCallbackReceived(node: Node) {
                handleNode(node, this)
            }
        })
    }

    // ── Mobile Approvals ──────────────────────────────────────────────────────

    private fun checkApprovals() {
        val journeyName = getString(R.string.forgerock_mobile_approval_service)
        showStatus("Checking for pending approvals…")
        FRSession.authenticate(this, journeyName, object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                runOnUiThread {
                    showStatus("Approval submitted successfully")
                    Toast.makeText(this@MainActivity, "Approval submitted", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onException(e: Exception) {
                Logger.error(tag, e.message, e)
                runOnUiThread {
                    showStatus("Not authenticated")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("No Pending Approvals")
                        .setMessage("There are no pending approval requests, or your device is not bound.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onCallbackReceived(node: Node) {
                handleNode(node, this)
            }
        })
    }

    // ── Shared callback handler ───────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.M)
    private fun handleNode(node: Node, listener: NodeListener<FRSession>) {
        val activity = this

        var nodeDialog = supportFragmentManager.findFragmentByTag(NodeDialogFragment.TAG) as? NodeDialogFragment
        nodeDialog?.dismiss()

        node.takeUnless { it.callbacks.isEmpty() }?.let {
            it.callbacks.forEach { callback ->
                when (callback.type) {
                    "DeviceBindingCallback" -> {
                        runOnUiThread {
                            node.getCallback(DeviceBindingCallback::class.java)
                                .bind(activity, listener = object : FRListener<Void?> {
                                    override fun onSuccess(result: Void?) {
                                        node.next(activity, listener)
                                    }

                                    override fun onException(e: Exception) {
                                        node.next(activity, listener)
                                    }
                                })
                        }
                    }

                    "DeviceSigningVerifierCallback" -> {
                        runOnUiThread {
                            node.getCallback(DeviceSigningVerifierCallback::class.java)
                                .sign(activity, listener = object : FRListener<Void?> {
                                    override fun onSuccess(result: Void?) {
                                        node.next(activity, listener)
                                    }

                                    override fun onException(e: Exception) {
                                        node.next(activity, listener)
                                    }
                                })
                        }
                    }

                    "WebAuthnAuthenticationCallback" -> {
                        node.getCallback(WebAuthnAuthenticationCallback::class.java)
                            ?.authenticate(activity, node, listener = object : FRListener<Void?> {
                                override fun onSuccess(result: Void?) = node.next(activity, listener)
                                override fun onException(e: Exception) = node.next(activity, listener)
                            })
                    }

                    "WebAuthnRegistrationCallback" -> {
                        node.getCallback(WebAuthnRegistrationCallback::class.java)
                            ?.register(activity, node = node, listener = object : FRListener<Void?> {
                                override fun onSuccess(result: Void?) = node.next(activity, listener)
                                override fun onException(e: Exception) = node.next(activity, listener)
                            })
                    }

                    "IdPCallback" -> {
                        node.getCallback(IdPCallback::class.java)
                            .signIn(null, object : FRListener<Void> {
                                override fun onSuccess(result: Void) = node.next(activity, listener)
                                override fun onException(e: Exception) {}
                            })
                    }

                    "DeviceProfileCallback" -> {
                        node.getCallback(DeviceProfileCallback::class.java)
                            ?.execute(activity, object : FRListener<Void> {
                                override fun onSuccess(result: Void) = node.next(activity, listener)
                                override fun onException(e: Exception) = node.next(activity, listener)
                            })
                    }

                    "SelectIdPCallback" -> {
                        node.getCallback(SelectIdPCallback::class.java).setValue("google_andy")
                        node.next(activity, listener)
                    }

                    "NameCallback", "PasswordCallback", "ChoiceCallback" -> {
                        currentNodeListener = listener
                        nodeDialog = NodeDialogFragment.newInstance(node)
                        nodeDialog?.show(supportFragmentManager, NodeDialogFragment.TAG)
                    }
                }
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    override fun logout() {
        FRUser.getCurrentUser()?.logout() ?: FRSession.getCurrentSession()?.logout()

        val existing = supportFragmentManager.findFragmentByTag(UserInfoFragment.TAG) as? UserInfoFragment
        existing?.let { supportFragmentManager.beginTransaction().remove(it).commit() }

        showMainButtons()
        updateLogoutVisibility()
        showStatus("Not authenticated")
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showStatus(msg: String) = runOnUiThread { status.text = msg }

    private fun showError(msg: String) = runOnUiThread {
        showStatus("Error")
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(msg)
            .setPositiveButton("OK", DialogInterface.OnClickListener { _, _ -> })
            .setCancelable(false)
            .show()
    }

    private fun updateLogoutVisibility() = runOnUiThread {
        btnLogout.visibility = if (FRUser.getCurrentUser() != null) View.VISIBLE else View.GONE
    }

    private fun hideMainButtons() = runOnUiThread {
        btnBindDevice.visibility = View.GONE
        btnLoginBiometrics.visibility = View.GONE
        btnCheckApprovals.visibility = View.GONE
        status.visibility = View.GONE
    }

    private fun showMainButtons() = runOnUiThread {
        btnBindDevice.visibility = View.VISIBLE
        btnLoginBiometrics.visibility = View.VISIBLE
        btnCheckApprovals.visibility = View.VISIBLE
        status.visibility = View.VISIBLE
    }
}
