package com.novapay.app

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
import org.forgerock.android.auth.callback.MetadataCallback
import org.forgerock.android.auth.callback.SelectIdPCallback
import org.forgerock.android.auth.callback.TextOutputCallback
import org.forgerock.android.auth.callback.WebAuthnAuthenticationCallback
import org.forgerock.android.auth.callback.WebAuthnRegistrationCallback
import org.forgerock.android.auth.devicebind.DeviceBindFragment
import org.forgerock.android.auth.exception.AuthenticationRequiredException


interface ActivityListener {
    fun logout()
    fun deviceBind()
    fun transactionSign()
    fun generateWebOtp()
}

class MainActivity : AppCompatActivity(), NodeListener<FRUser>, ActivityListener {

    private val status: TextView by lazy { findViewById(R.id.status) }
    private val loginButton: Button by lazy { findViewById(R.id.login) }
    private val logoutButton: Button by lazy { findViewById(R.id.logout) }
    private val deviceBindButton: Button by lazy { findViewById(R.id.deviceBind) }
    private val transactionSignButton: Button by lazy { findViewById(R.id.transactionSign) }
    private val resetBindingButton: Button by lazy { findViewById(R.id.resetBinding) }
    private val classNameTag = MainActivity::class.java.name
    private val prefs by lazy { getSharedPreferences("device_binding_prefs", MODE_PRIVATE) }
    private var pendingPaymentClaims: Map<String, Any> = emptyMap()
    var lastSignedJwt: String? = null
        private set
    private var capturedBindingUsername: String? = null

    private fun isDeviceBound(): Boolean {
        val bound = prefs.getBoolean("is_bound", false)
        Logger.debug(classNameTag, "isDeviceBound() -> $bound (SharedPrefs key='is_bound')")
        return bound
    }

    private fun markDeviceBound(username: String) {
        Logger.debug(classNameTag, "markDeviceBound() called — persisting is_bound=true, username=$username")
        prefs.edit().putBoolean("is_bound", true).putString("bound_username", username).apply()
        Logger.debug(classNameTag, "markDeviceBound() complete")
    }

    private fun clearDeviceBound() {
        Logger.debug(classNameTag, "clearDeviceBound() called — removing is_bound from SharedPrefs (test reset)")
        prefs.edit().remove("is_bound").remove("bound_username").apply()
        Logger.debug(classNameTag, "clearDeviceBound() complete — isDeviceBound() now=${isDeviceBound()}")
    }

    private fun boundUsername(): String? = prefs.getString("bound_username", null)

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
        resetBindingButton.setOnClickListener {
            Logger.debug(classNameTag, "resetBindingButton clicked — triggering test reset of device binding")
            clearDeviceBound()
            updateStatus(true)
            showDialog("Dev Reset", "Device binding cleared. You can bind again.")
        }
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
                r.setLoginHint("demo@example.com")
                r.setPrompt("login")
            }.customTabsIntent { t: CustomTabsIntent.Builder ->
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
            Logger.debug(classNameTag, "updateStatus(showLogin=$showLogin) — checking device binding state")
            val bound = isDeviceBound()
            Logger.debug(classNameTag, "updateStatus: bound=$bound -> loginVisible=${showLogin && bound}, logoutVisible=${!showLogin && bound}")
            loginButton.visibility = if (showLogin && bound) View.VISIBLE else View.GONE
            loginButton.isEnabled = showLogin && bound
            logoutButton.visibility = if (!showLogin && bound) View.VISIBLE else View.GONE
            logoutButton.isEnabled = !showLogin && bound
            status.visibility = View.VISIBLE
            status.text = when {
                !bound -> "Bind this device to get started"
                showLogin -> "Welcome back, ${boundUsername()?.takeIf { it.isNotEmpty() } ?: "there"}! Sign in to continue."
                else -> "User is authenticated"
            }
            Logger.debug(classNameTag, "updateStatus: statusText='${status.text}'")
            updateSecurityButtons()
        }
    }

    private fun updateSecurityButtons() {
        Logger.debug(classNameTag, "updateSecurityButtons() — checking device binding state")
        val bound = isDeviceBound()
        Logger.debug(classNameTag, "updateSecurityButtons: bound=$bound -> deviceBindVisible=${!bound}, transactionSignVisible=$bound, resetBindingVisible=$bound")
        deviceBindButton.visibility = if (!bound) View.VISIBLE else View.GONE
        transactionSignButton.visibility = if (bound) View.VISIBLE else View.GONE
        resetBindingButton.visibility = if (bound) View.VISIBLE else View.GONE
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
        Logger.debug(classNameTag, "launchUserInfoFragment() — checking device binding state for fragment")
        val boundForFragment = isDeviceBound()
        Logger.debug(classNameTag, "launchUserInfoFragment: passing isBound=$boundForFragment to UserInfoFragment")
        val userInfoFragment = UserInfoFragment.newInstance(result?.accessToken?.value,
            token.refreshToken ?: "N/A",
            token.idToken ?: "N/A",
            this@MainActivity,
            boundForFragment)
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
            dialogBuilder.setMessage("Login Failed. Retry Again")
                .setCancelable(false)
                .setPositiveButton("OK", DialogInterface.OnClickListener { _, _ -> })
            val alert = dialogBuilder.create()
            alert.setTitle("Unauthorized")
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
                            deviceBindingCallback.bind(activity,
                                deviceAuthenticator = brandedDeviceAuthenticator(),
                                listener = object : FRListener<Void?> {
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
                            deviceBindingCallback.sign(activity,
                                deviceAuthenticator = brandedDeviceAuthenticator(),
                                listener = object : FRListener<Void?> {
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
        capturedBindingUsername = null
        val listener = object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                val username = capturedBindingUsername ?: ""
                Logger.debug(classNameTag, "deviceBind() onSuccess — capturedBindingUsername=$capturedBindingUsername, saving username='$username'")
                markDeviceBound(username)
                Logger.debug(classNameTag, "deviceBind() onSuccess — isDeviceBound() now=${isDeviceBound()}")
                runOnUiThread {
                    updateStatus(showLogin = true)
                    showDialog("Device Bound", "Your device is now registered. Sign in to continue.")
                }
            }
            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message, e)
                runOnUiThread { showDialog("Device Binding Failed", e.message ?: "Unknown error") }
            }
            override fun onCallbackReceived(node: Node) {
                handleSessionNode(node, this)
            }
        }
        FRSession.authenticate(applicationContext, getString(R.string.am_device_bind_service), listener)
    }

    override fun transactionSign() {
        Logger.debug(classNameTag, "transactionSign() called — showing PaymentDialogFragment")
        val dialog = PaymentDialogFragment()
        dialog.onConfirm = { amount, recipient ->
            Logger.debug(classNameTag, "Payment confirmed: amount=$amount recipient=$recipient — starting transaction signing flow")
            pendingPaymentClaims = mapOf(
                "amount" to amount,
                "currency" to "GBP",
                "recipient" to recipient,
                "timestamp" to System.currentTimeMillis()
            )
            startTransactionSignFlow()
        }
        dialog.show(supportFragmentManager, PaymentDialogFragment.TAG)
    }

    override fun generateWebOtp() {
        Logger.debug(classNameTag, "generateWebOtp() called — authenticating bound device against WebOTP tree")
        val listener = object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                Logger.debug(classNameTag, "generateWebOtp() onSuccess — AM tree completed")
            }
            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message, e)
                runOnUiThread { showDialog("Web OTP Failed", e.message ?: "Unknown error") }
            }
            override fun onCallbackReceived(node: Node) {
                handleSessionNode(node, this)
            }
        }
        FRSession.authenticate(applicationContext, getString(R.string.am_web_otp_service), listener)
    }

    private fun startTransactionSignFlow() {
        val listener = object : NodeListener<FRSession> {
            override fun onSuccess(result: FRSession) {
                val jwt = lastSignedJwt
                pendingPaymentClaims = emptyMap()
                Logger.debug(classNameTag, "Transaction signing complete — JWT ready to send to payment service")
                runOnUiThread {
                    showDialog("Transaction Signing", "Transaction signed successfully")
                }
            }
            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message, e)
                pendingPaymentClaims = emptyMap()
                lastSignedJwt = null
                runOnUiThread { showDialog("Transaction Signing Failed", e.message ?: "Unknown error") }
            }
            override fun onCallbackReceived(node: Node) {
                handleSessionNode(node, this)
            }
        }
        FRSession.authenticate(applicationContext, getString(R.string.am_transaction_sign_service), listener)
    }

    private fun dismissNodeDialog() {
        (supportFragmentManager.findFragmentByTag(NodeDialogFragment.TAG) as? NodeDialogFragment)?.dismiss()
    }

    private fun handleSessionNode(node: Node, listener: NodeListener<FRSession>) {
        val activity = this
        var dialogShown = false
        node.callbacks.forEach { callback ->
            when (callback.type) {
                "DeviceBindingCallback" -> {
                    runOnUiThread {
                        dismissNodeDialog()
                        node.getCallback(DeviceBindingCallback::class.java).bind(activity,
                            deviceAuthenticator = brandedDeviceAuthenticator(),
                            listener = object : FRListener<Void?> {
                            override fun onSuccess(result: Void?) { node.next(activity, listener) }
                            override fun onException(e: Exception) { node.next(activity, listener) }
                        })
                    }
                }
                "DeviceSigningVerifierCallback" -> {
                    runOnUiThread {
                        dismissNodeDialog()
                        Logger.debug(classNameTag, "DeviceSigningVerifierCallback — signing with customClaims=$pendingPaymentClaims")
                        node.getCallback(DeviceSigningVerifierCallback::class.java).sign(
                            activity,
                            customClaims = pendingPaymentClaims,
                            deviceAuthenticator = brandedDeviceAuthenticator { jws ->
                                lastSignedJwt = jws
                                Logger.debug(classNameTag, "SIGNED_JWT: $jws")
                            },
                            listener = object : FRListener<Void?> {
                                override fun onSuccess(result: Void?) { node.next(activity, listener) }
                                override fun onException(e: Exception) { node.next(activity, listener) }
                            }
                        )
                    }
                }
                "MetadataCallback" -> {
                    // AM returns OTP + TTL as JSON: {"data":{"otp":"123456","ttl":300}}
                    runOnUiThread {
                        val meta = node.getCallback(MetadataCallback::class.java).value
                        val otp = meta?.optString("otp", "") ?: ""
                        val ttl = meta?.optLong("ttl", 300L) ?: 300L
                        Logger.debug(classNameTag, "MetadataCallback — received web OTP (ttl=${ttl}s)")
                        if (otp.isNotEmpty()) {
                            WebOtpDialogFragment.newInstance(otp, ttl)
                                .show(supportFragmentManager, WebOtpDialogFragment.TAG)
                        }
                        node.next(activity, listener)
                    }
                }
                "TextOutputCallback" -> {
                    // AM returns OTP as a plain text message
                    runOnUiThread {
                        val otp = node.getCallback(TextOutputCallback::class.java).message
                        Logger.debug(classNameTag, "TextOutputCallback — received web OTP")
                        if (!otp.isNullOrEmpty()) {
                            WebOtpDialogFragment.newInstance(otp)
                                .show(supportFragmentManager, WebOtpDialogFragment.TAG)
                        }
                        node.next(activity, listener)
                    }
                }
                else -> {
                    if (!dialogShown) {
                        dialogShown = true
                        val nodeDialog = (supportFragmentManager.findFragmentByTag(NodeDialogFragment.TAG) as? NodeDialogFragment)
                            ?.also { it.dismiss() }
                            .let { NodeDialogFragment.newInstance(node) }
                            .also {
                                it.nodeListener = listener
                                it.onValuesCaptured = { username ->
                                    Logger.debug(classNameTag, "onValuesCaptured: username=$username")
                                    capturedBindingUsername = username
                                }
                            }
                        runOnUiThread { nodeDialog.show(supportFragmentManager, NodeDialogFragment.TAG) }
                    }
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
