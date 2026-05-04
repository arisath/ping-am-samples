package com.novapay.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class WebOtpDialogFragment : DialogFragment() {

    private var otp: String = ""
    private var ttlSeconds: Long = 300L
    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_web_otp, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val otpCodeView: TextView = view.findViewById(R.id.otpCode)
        val countdownView: TextView = view.findViewById(R.id.otpCountdown)
        val copyButton: Button = view.findViewById(R.id.copyOtp)
        val dismissButton: Button = view.findViewById(R.id.dismissOtp)

        otpCodeView.text = formatOtp(otp)

        copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Web OTP", otp))
            Toast.makeText(requireContext(), "OTP copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dismissButton.setOnClickListener { dismiss() }

        countDownTimer = object : CountDownTimer(ttlSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val min = millisUntilFinished / 60000L
                val sec = (millisUntilFinished % 60000L) / 1000L
                countdownView.text = "Expires in %d:%02d".format(min, sec)
            }
            override fun onFinish() {
                countdownView.text = "Expired"
                copyButton.isEnabled = false
                otpCodeView.alpha = 0.4f
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }

    private fun formatOtp(code: String): String =
        if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code

    companion object {
        const val TAG = "WebOtpDialogFragment"

        fun newInstance(otp: String, ttlSeconds: Long = 300L) =
            WebOtpDialogFragment().apply {
                this.otp = otp
                this.ttlSeconds = ttlSeconds
            }
    }
}
