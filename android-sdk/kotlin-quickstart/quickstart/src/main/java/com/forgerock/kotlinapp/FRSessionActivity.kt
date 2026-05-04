package com.novapay.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import net.openid.appauth.AuthorizationRequest
import org.forgerock.android.auth.*
import java.lang.Exception


class FRSessionActivity: AppCompatActivity(), NodeListener<FRSession>, ActivityListener {

    private val status: TextView by lazy { findViewById(R.id.status) }
    private val loginButton: Button by lazy { findViewById(R.id.login) }
    private val logoutButton: Button by lazy { findViewById(R.id.logout) }
    private val classNameTag = FRSessionActivity::class.java.name
    private var userInfoFragment: UserInfoFragment? = null
    private var nodeDialog: NodeDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        updateStatus(showLogin = true)
        loginButton.setOnClickListener {
            val journeyName = "SimpleLogin"
            FRSession.authenticate(this, journeyName, this)
        }
        logoutButton.setOnClickListener {
            logout()
        }
    }

    override fun onSuccess(result: FRSession) {
        getAccessToken()
    }


    override fun onException(e: Exception) {
      print("------> $e")
    }

    override fun onCallbackReceived(node: Node) {
        nodeDialog?.dismiss()
        nodeDialog = NodeDialogFragment.newInstance(node)
        nodeDialog?.show(supportFragmentManager, NodeDialogFragment::class.java.name)
    }


    private fun getAccessToken() {
        FRUser.getCurrentUser()?.getAccessToken(object : FRListener<AccessToken> {
            override fun onSuccess(token: AccessToken) {
                runOnUiThread {
                    loginButton.visibility = View.GONE
                    logoutButton.visibility = View.GONE
                    status.visibility = View.GONE
                    launchUserInfoFragment(token)
                }
            }

            override fun onException(e: Exception) {
                Logger.error(classNameTag, e.message)
            }

        })
    }

    private fun updateStatus(showLogin: Boolean = false) {
        runOnUiThread {
            (if(showLogin) View.VISIBLE else View.GONE).also {
                loginButton.visibility = it
                logoutButton.visibility = it
                status.visibility = it
            }
            loginButton.apply { this.isEnabled = showLogin == true }
            logoutButton.apply { this.isEnabled = showLogin == false }
            status.text = if(showLogin) "User is not authenticated" else "User is authenticated"
        }
    }


    private fun launchUserInfoFragment(token: AccessToken) {
        userInfoFragment = UserInfoFragment.newInstance(
            token.value,
            token.refreshToken,
            token.idToken,
            this
        )
        userInfoFragment?.let {
            supportFragmentManager.beginTransaction()
                .add(R.id.container, it).commit()
        }
    }

    override fun logout() {
        FRSession.getCurrentSession().logout()
        userInfoFragment?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        updateStatus(true)
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
                nodeDialog?.dismiss()
                nodeDialog = NodeDialogFragment.newInstance(node).also { it.nodeListener = this }
                runOnUiThread { nodeDialog?.show(supportFragmentManager, NodeDialogFragment::class.java.name) }
            }
        }
        FRSession.authenticate(this, getString(R.string.am_device_bind_service), listener)
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
                nodeDialog?.dismiss()
                nodeDialog = NodeDialogFragment.newInstance(node).also { it.nodeListener = this }
                runOnUiThread { nodeDialog?.show(supportFragmentManager, NodeDialogFragment::class.java.name) }
            }
        }
        FRSession.authenticate(this, getString(R.string.am_transaction_sign_service), listener)
    }

    override fun generateWebOtp() {
        // Not implemented in this demo activity — use MainActivity for the full Web OTP flow
    }

    private fun showDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .create()
            .show()
    }

}
