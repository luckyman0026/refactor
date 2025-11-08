package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.EventStatus
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.repository.WithdrawalEventOutboxRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventsService @Autowired constructor(
    private val outboxRepository: WithdrawalEventOutboxRepository
) {

    /**
     * Saves event to outbox within the same transaction.
     * This ensures that if the withdrawal status is updated, the event is guaranteed to be stored.
     * A separate processor will send events from the outbox asynchronously.
     */
    @Transactional
    fun send(withdrawal: Withdrawal) {
        val event = WithdrawalEventOutbox(
            withdrawalId = withdrawal.id,
            withdrawalType = "WITHDRAWAL",
            status = withdrawal.status?.name,
            amount = withdrawal.amount,
            transactionId = withdrawal.transactionId,
            userId = withdrawal.userId,
            paymentMethodId = withdrawal.paymentMethodId,
            eventStatus = EventStatus.PENDING
        )
        outboxRepository.save(event)
    }

    /**
     * Saves event to outbox within the same transaction.
     * This ensures that if the withdrawal status is updated, the event is guaranteed to be stored.
     * A separate processor will send events from the outbox asynchronously.
     */
    @Transactional
    fun send(withdrawal: WithdrawalScheduled) {
        val event = WithdrawalEventOutbox(
            withdrawalId = withdrawal.id,
            withdrawalType = "WITHDRAWAL_SCHEDULED",
            status = withdrawal.status?.name,
            amount = withdrawal.amount,
            transactionId = withdrawal.transactionId,
            userId = withdrawal.userId,
            paymentMethodId = withdrawal.paymentMethodId,
            eventStatus = EventStatus.PENDING
        )
        outboxRepository.save(event)
    }

    /**
     * Actually sends the event to the message queue.
     * This is called by the OutboxProcessor after the event is successfully stored.
     */
    fun sendToQueue(event: WithdrawalEventOutbox) {
        // build and send an event in message queue
        // This is where you would integrate with Kafka, RabbitMQ, etc.
        // For now, we just emulate it
    }
}
