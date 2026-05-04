package com.novapay.app

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.forgerock.android.auth.Logger

private const val ARG_ACCESS_TOKEN = "access_token"
private const val ARG_REFRESH_TOKEN = "refresh_token"
private const val ARG_ID_TOKEN = "id_token"
private const val ARG_IS_BOUND = "is_bound"

class UserInfoFragment : Fragment() {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var idToken: String? = null
    private var isBound: Boolean = false
    private var listener: ActivityListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            accessToken = it.getString(ARG_ACCESS_TOKEN)
            refreshToken = it.getString(ARG_REFRESH_TOKEN)
            idToken = it.getString(ARG_ID_TOKEN)
            isBound = it.getBoolean(ARG_IS_BOUND, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.debug(TAG, "onViewCreated: isBound=$isBound — setting up buttons accordingly")

        val accessTokenView: TextView = view.findViewById(R.id.accessToken)
        accessTokenView.movementMethod = ScrollingMovementMethod()
        accessTokenView.text = accessToken
        val refreshTokenView: TextView = view.findViewById(R.id.refreshToken)
        refreshTokenView.movementMethod = ScrollingMovementMethod()
        refreshTokenView.text = refreshToken
        val idTokenView: TextView = view.findViewById(R.id.idToken)
        idTokenView.movementMethod = ScrollingMovementMethod()
        idTokenView.text = idToken

        val logout: Button = view.findViewById(R.id.logout)
        logout.setOnClickListener { listener?.logout() }

        val deviceBind: Button = view.findViewById(R.id.deviceBind)
        deviceBind.visibility = if (!isBound) View.VISIBLE else View.GONE
        Logger.debug(TAG, "onViewCreated: deviceBind button visible=${!isBound} (device not yet bound)")
        deviceBind.setOnClickListener { listener?.deviceBind() }

        val transactionSign: Button = view.findViewById(R.id.transactionSign)
        transactionSign.visibility = if (isBound) View.VISIBLE else View.GONE
        Logger.debug(TAG, "onViewCreated: transactionSign button visible=$isBound (device is bound)")
        transactionSign.setOnClickListener { listener?.transactionSign() }

        val generateWebOtp: Button = view.findViewById(R.id.generateWebOtp)
        generateWebOtp.visibility = if (isBound) View.VISIBLE else View.GONE
        Logger.debug(TAG, "onViewCreated: generateWebOtp button visible=$isBound (device is bound)")
        generateWebOtp.setOnClickListener { listener?.generateWebOtp() }
    }

    companion object {
        @JvmStatic
        fun newInstance(accessToken: String?, refreshToken: String?, idToken: String?, listener: ActivityListener?, isBound: Boolean = false) =
            UserInfoFragment().apply {
                this.listener = listener
                arguments = Bundle().apply {
                    putString(ARG_ACCESS_TOKEN, accessToken)
                    putString(ARG_REFRESH_TOKEN, refreshToken)
                    putString(ARG_ID_TOKEN, idToken)
                    putBoolean(ARG_IS_BOUND, isBound)
                }
            }
        const val TAG: String = "UserInfoFragment"
    }
}
