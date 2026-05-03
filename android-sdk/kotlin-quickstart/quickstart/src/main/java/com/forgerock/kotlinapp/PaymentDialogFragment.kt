/*
 * Copyright (c) 2022 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.forgerock.kotlinapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.TextView

class PaymentDialogFragment : DialogFragment() {

    var onConfirm: ((amount: String, recipient: String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_payment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recipientEdit: TextInputEditText = view.findViewById(R.id.recipient)
        val amountEdit: TextInputEditText = view.findViewById(R.id.amount)
        val recipientLayout: TextInputLayout = view.findViewById(R.id.recipientLayout)
        val amountLayout: TextInputLayout = view.findViewById(R.id.amountLayout)
        val summaryCard: MaterialCardView = view.findViewById(R.id.summaryCard)
        val summaryText: TextView = view.findViewById(R.id.summaryText)
        val signButton: MaterialButton = view.findViewById(R.id.sign)
        val cancelButton: MaterialButton = view.findViewById(R.id.cancel)

        signButton.setOnClickListener {
            val recipient = recipientEdit.text?.toString().orEmpty().trim()
            val amount = amountEdit.text?.toString().orEmpty().trim()

            recipientLayout.error = null
            amountLayout.error = null

            var valid = true
            if (recipient.isEmpty()) {
                recipientLayout.error = "Required"
                valid = false
            }
            if (amount.isEmpty() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                amountLayout.error = "Enter a valid amount"
                valid = false
            }
            if (!valid) return@setOnClickListener

            // First tap: show summary and switch button to "Sign"
            if (summaryCard.visibility != View.VISIBLE) {
                summaryText.text = "Pay £$amount to $recipient"
                summaryCard.visibility = View.VISIBLE
                signButton.text = "Sign"
                recipientEdit.isEnabled = false
                amountEdit.isEnabled = false
                return@setOnClickListener
            }

            // Second tap (after review): confirm and sign
            dismiss()
            onConfirm?.invoke(amount, recipient)
        }

        cancelButton.setOnClickListener { dismiss() }
    }

    override fun onResume() {
        super.onResume()
        val params = dialog?.window?.attributes
        params?.width = ViewGroup.LayoutParams.MATCH_PARENT
        params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog?.window?.attributes = params as? WindowManager.LayoutParams
    }

    companion object {
        const val TAG = "PaymentDialogFragment"
    }
}
