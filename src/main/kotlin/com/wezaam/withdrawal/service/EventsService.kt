package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.repository.WithdrawalEventOutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class EventsService(
    private val outboxRepository: WithdrawalEventOutboxRepository
) {

    @Transactional
    fun send(withdrawal: Withdrawal) {
        val event = WithdrawalEventOutbox(
            withdrawalId = withdrawal.id,
            withdrawalType = "WITHDRAWAL",
            status = withdrawal.status?.name,
            transactionId = withdrawal.transactionId,
            amount = withdrawal.amount,
            userId = withdrawal.userId,
            paymentMethodId = withdrawal.paymentMethodId,
            createdAt = Instant.now()
        )
        outboxRepository.save(event)
    }

    @Transactional
    fun send(withdrawal: WithdrawalScheduled) {
        val event = WithdrawalEventOutbox(
            withdrawalId = withdrawal.id,
            withdrawalType = "WITHDRAWAL_SCHEDULED",
            status = withdrawal.status?.name,
            transactionId = withdrawal.transactionId,
            amount = withdrawal.amount,
            userId = withdrawal.userId,
            paymentMethodId = withdrawal.paymentMethodId,
            createdAt = Instant.now()
        )
        outboxRepository.save(event)
    }
}

