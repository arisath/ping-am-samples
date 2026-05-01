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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import net.openid.appauth.AuthorizationRequest
import org.forgerock.android.auth.AccessToken
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
import org.forgerock.android.auth.devicebind.DeviceBindFragment
import org.forgerock.android.auth.exception.AuthenticationRequiredException


interface ActivityListener {
    fun logout()
    fun deviceBind()
    fun transactionSign()
}

class MainActivity : AppCompatActivity(), NodeListener<FRUser>, ActivityListener {

    private val status: TextView by lazy { findViewById(R.id.status) }
    private val loginButton: Button by lazy { findViewById(R.id.login) }
    private val logoutButton: Button by lazy { findViewById(R.id.logout) }
    private val deviceBindButton: Button by lazy { findViewById(R.id.deviceBind) }
    private val transactionSignButton: Button by lazy { findViewById(R.id.transactionSign) }
    private val classNameTag = MainActivity::class.java.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStatus()
        loginButton.setOnClickListener {
            if (BuildConfig.embeddedLogin) {
                FRUser.login(applicationContext, this)
            } else {
                centralizedLogin()
            }
        }
        logoutButton.setOnClickListener { logout() }
        deviceBindButton.setOnClickListener { deviceBind() }
        transactionSignButton.setOnClickListener { transactionSign() }
    }

    override fun onStart() {
        super.onStart()

        val existing = supportFragmentManager.findFragmentByTag(UserInfoFragment.TAG) as? UserInfoFragment
        existing?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        if (FRUser.getCurrentUser() == null) {
            updateStatus(true)
        } else {
            try {
                val currentUser = FRUser.getCurrentUser()
                updateStatus(false)
                launchUserInfoFragment(currentUser.accessToken, currentUser)
            } catch (e: AuthenticationRequiredException) {
                updateStatus(true)
            }

        }
    }

    private fun centralizedLogin() {
        FRUser.browser().appAuthConfigurer()
            .authorizationRequest { r: AuthorizationRequest.Builder ->
                // Add a login hint parameter about the user:
                r.setLoginHint("demo@example.com")
                // Request that the user re-authenticates:
                r.setPrompt("login")
            }.customTabsIntent { t: CustomTabsIntent.Builder ->
                // Customize the browser:
                t.setShowTitle(true)
                t.setToolbarColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }.done().login(this, object : FRListener<FRUser?> {
                override fun onSuccess(result: FRUser?) {
                    Logger.debug(classNameTag, result?.accessToken?.value)
                    getUserInfo(result)
                }

                override fun onException(e: java.lang.Exception) {
                    Logger.error(classNameTag, e.message)
                }
            })
    }


    private fun updateStatus(showLogin: Boolean = false) {
        runOnUiThread {
            loginButton.visibility = if (showLogin) View.VISIBLE else View.GONE
            loginButton.isEnabled = showLogin
            logoutButton.visibility = if (!showLogin) View.VISIBLE else View.GONE
            logoutButton.isEnabled = !showLogin
            deviceBindButton.visibility = View.VISIBLE
            transactionSignButton.visibility = View.VISIBLE
            status.visibility = View.VISIBLE
            status.text = if (showLogin) "User is not authenticated" else "User is authenticated"
        }
    }


    private fun getUserInfo(result: FRUser?) {
        result?.getAccessToken(object : FRListener<AccessToken> {
            override fun onSuccess(token: AccessToken) {
                runOnUiThread {
                    loginButton.visibility = View.GONE
                    logoutButton.visibility = View.GONE
                    status.visibility = View.GONE
                    launchUserInfoFragment(token, result)
                }
            }

            override fun onException(e: java.lang.Exception) {

            }
        })
    }


    private fun launchUserInfoFragment(token: AccessToken, result: FRUser?) {
        val userInfoFragment = UserInfoFragment.newInstance(result?.accessToken?.value,
            token.refreshToken ?: "N/A",
            token.idToken ?: "N/A",
            this@MainActivity)
        userInfoFragment.let {
            supportFragmentManager.beginTransaction().add(R.id.container, it, UserInfoFragment.TAG).commit()
        }
    }

    override fun onSuccess(result: FRUser) {
        getUserInfo(result)
    }

    override fun onException(e: Exception) {
        Logger.error(classNameTag, e?.message, e)
        runOnUiThread {
            val dialogBuilder = AlertDialog.Builder(this)
            // set message of alert dialog
            dialogBuilder.setMessage("Login Failed. Retry Again")
                // if the dialog is cancelable
                .setCancelable(false)
                // positive button text and action
                .setPositiveButton("OK", DialogInterface.OnClickListener { _, _ -> })
            // create dialog box
            val alert = dialogBuilder.create()
            // set title for alert dialog box
            alert.setTitle("Unauthorized")
            // show alert dialog
            alert.show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCallbackReceived(node: Node) {
        val activity = this

        var nodeDialog = supportFragmentManager.findFragmentByTag(NodeDialogFragment.TAG) as? NodeDialogFragment
        nodeDialog?.dismiss()

        node?.takeUnless { it.callbacks.isEmpty() }?.let {
            it.callbacks.forEach { typer ->
                when (typer.type) {
                    "DeviceBindingCallback" -> {
                        runOnUiThread {
                            val deviceBindingCallback =
                                node.getCallback(DeviceBindingCallback::class.java)
                            deviceBindingCallback.bind(activity, listener =  object : FRListener<Void?> {
                                override fun onSuccess(result: Void?) {
                                    node.next(activity, activity)
                                }

                                override fun onException(e: java.lang.Exception) {
                                    node.next(activity, activity)
                                }
                            })
                        }
                    }
                    "DeviceSigningVerifierCallback" -> {
                        runOnUiThread {
                            val deviceBindingCallback =
                                node.getCallback(DeviceSigningVerifierCallback::class.java)
                            deviceBindingCallback.sign(activity, listener = object : FRListener<Void?> {
                                override fun onSuccess(result: Void?) {
                                    node.next(activity, activity)
                                }

                                override fun onException(e: java.lang.Exception) {
                                    node.next(activity, activity)
                                }
                            })
                        }
                    }
                    "WebAuthnAuthenticationCallback" -> {
                        val webAuthCallback =
                            node.getCallback(WebAuthnAuthenticationCallback::class.java)
                        webAuthCallback?.authenticate(this, node, listener = object : FRListener<Void?> {
                            override fun onException(e: Exception) {
                                node.next(activity, activity)
                            }

                            override fun onSuccess(result: Void?) {
                                node.next(activity, activity)
                            }
                        })
                    }
                    "WebAuthnRegistrationCallback" -> {
                        val callback = node.getCallback(WebAuthnRegistrationCallback::class.java)
                        callback?.register(this, node= node, listener = object : FRListener<Void?> {
                            override fun onSuccess(result: Void?) {
                                node.next(activity, activity)
                            }

                            override fun onException(e: java.lang.Exception) {
                                node.next(activity, activity)
                            }
                        })
                    }
                    "IdPCallback" -> {
                        val idp: IdPCallback = node.getCallback(IdPCallback::class.java)
                        idp.signIn(null, object : FRListener<Void> {
                            override fun onSuccess(result: Void) {
                                node.next(activity, activity)
                            }

                            override fun onException(e: java.lang.Exception) {
                            }

                        })
                    }
                    "DeviceProfileCallback" -> {
                        val deviceProfileCallback =
                            node.getCallback(DeviceProfileCallback::class.java)

                        deviceProfileCallback?.execute(activity, object : FRListener<Void> {
                            override fun onException(e: Exception) {
                                node.next(activity, activity)
                            }

                            override fun onSuccess(result: Void) {
                                node.next(activity, activity)
                            }
                        })
                    }
                    "SelectIdPCallback" -> {
                        val idp: SelectIdPCallback = node.getCallback(SelectIdPCallback::class.java)
                        idp.setValue("google_andy")
                        node.next(activity, activity)
                    }
                    "NameCallback" -> {
                        nodeDialog?.dismiss()
                        nodeDialog = NodeDialogFragment.newInstance(it)
                        nodeDialog?.show(supportFragmentManager,
                            NodeDialogFragment.TAG)
                    }
                    "PasswordCallback" -> {
                        nodeDialog?.dismiss()
                        nodeDialog = NodeDialogFragment.newInstance(it)
                        nodeDialog?.show(supportFragmentManager,
                            NodeDialogFragment.TAG)
                    }
                    "ChoiceCallback" -> {
                        nodeDialog?.dismiss()
                        nodeDialog = NodeDialogFragment.newInstance(it)
                        nodeDialog?.show(supportFragmentManager,
                            NodeDialogFragment.TAG)
                    }
                }
            }

        }
    }

    override fun deviceBind() {
        val listener = object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                runOnUiThread { showDialog("Device Binding", "Device bound successfully") }
            }
            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message, e)
                runOnUiThread { showDialog("Device Binding Failed", e.message ?: "Unknown error") }
            }
            override fun onCallbackReceived(node: Node) {
                handleSessionNode(node, this)
            }
        }
        FRSession.authenticate(applicationContext, getString(R.string.forgerock_device_bind_service), listener)
    }

    override fun transactionSign() {
        val listener = object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                runOnUiThread { showDialog("Transaction Signing", "Transaction signed successfully") }
            }
            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message, e)
                runOnUiThread { showDialog("Transaction Signing Failed", e.message ?: "Unknown error") }
            }
            override fun onCallbackReceived(node: Node) {
                handleSessionNode(node, this)
            }
        }
        FRSession.authenticate(applicationContext, getString(R.string.forgerock_transaction_sign_service), listener)
    }

    private fun handleSessionNode(node: Node, listener: NodeListener<FRSession>) {
        val activity = this
        node.callbacks.forEach { callback ->
            when (callback.type) {
                "DeviceBindingCallback" -> {
                    runOnUiThread {
                        node.getCallback(DeviceBindingCallback::class.java).bind(activity, listener = object : FRListener<Void?> {
                            override fun onSuccess(result: Void?) { node.next(activity, listener) }
                            override fun onException(e: Exception) { node.next(activity, listener) }
                        })
                    }
                }
                "DeviceSigningVerifierCallback" -> {
                    runOnUiThread {
                        node.getCallback(DeviceSigningVerifierCallback::class.java).sign(activity, listener = object : FRListener<Void?> {
                            override fun onSuccess(result: Void?) { node.next(activity, listener) }
                            override fun onException(e: Exception) { node.next(activity, listener) }
                        })
                    }
                }
                else -> {
                    var nodeDialog = supportFragmentManager.findFragmentByTag(NodeDialogFragment.TAG) as? NodeDialogFragment
                    nodeDialog?.dismiss()
                    nodeDialog = NodeDialogFragment.newInstance(node).also { it.nodeListener = listener }
                    runOnUiThread { nodeDialog.show(supportFragmentManager, NodeDialogFragment.TAG) }
                }
            }
        }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .create()
            .show()
    }

    override fun logout() {
        FRUser.getCurrentUser().logout()

        val existing = supportFragmentManager.findFragmentByTag(UserInfoFragment.TAG) as? UserInfoFragment
        existing?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        updateStatus(true)
    }
}