package com.novapay.app

import android.content.DialogInterface
import android.os.Bundle
import android.os.OperationCanceledException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import org.forgerock.android.auth.devicebind.Prompt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BrandedPinFragment : DialogFragment() {

    private var prompt: Prompt? = null

    var continuation: CancellableContinuation<CharArray>? = null
        set(value) {
            field = value
            field?.invokeOnCancellation {
                if (it is TimeoutCancellationException && isVisible) dismiss()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            prompt = it.getParcelable(ARG_PROMPT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_branded_pin, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.pin_title).text = prompt?.title?.ifBlank { "Verify Your Identity" } ?: "Verify Your Identity"
        view.findViewById<TextView>(R.id.pin_subtitle).text = prompt?.subtitle?.ifBlank { "NovaPay" } ?: "NovaPay"
        view.findViewById<TextView>(R.id.pin_description).text = prompt?.description?.ifBlank { "Enter your PIN to continue" } ?: "Enter your PIN to continue"

        val pinInput: TextInputEditText = view.findViewById(R.id.pin_input)

        view.findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            val pin = pinInput.text?.toString().orEmpty()
            if (pin.isNotEmpty()) {
                continuation?.resume(pin.toCharArray())
                dismiss()
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dismiss()
            continuation?.resumeWithException(OperationCanceledException())
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        continuation?.resumeWithException(OperationCanceledException())
    }

    override fun onDestroy() {
        super.onDestroy()
        continuation?.takeUnless { it.isCompleted }?.cancel()
    }

    companion object {
        const val TAG = "BrandedPinFragment"
        private const val ARG_PROMPT = "prompt"

        fun newInstance(prompt: Prompt, continuation: CancellableContinuation<CharArray>) =
            BrandedPinFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_PROMPT, prompt) }
                this.continuation = continuation
            }
    }
}
