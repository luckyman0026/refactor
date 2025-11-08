package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.exception.TransactionException
import com.wezaam.withdrawal.model.PaymentMethod
import org.springframework.stereotype.Component

@Component
class WithdrawalProcessingService {

    @Throws(TransactionException::class)
    fun sendToProcessing(amount: Double?, paymentMethod: PaymentMethod?): Long {
        // call a payment provider
        // in case a transaction can be process
        // it generates a transactionId and process the transaction async
        // otherwise it throws TransactionException
        return System.nanoTime()
    }
}

