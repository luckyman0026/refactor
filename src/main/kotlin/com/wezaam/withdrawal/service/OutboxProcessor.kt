package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import com.wezaam.withdrawal.repository.WithdrawalEventOutboxRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OutboxProcessor(
    private val outboxRepository: WithdrawalEventOutboxRepository
) {
    private val maxRetries = 3

    @Scheduled(fixedDelay = 2000)
    @Transactional
    fun processOutbox() {
        val pendingEvents = outboxRepository.findByProcessedAtIsNullOrderByCreatedAtAsc()
        
        pendingEvents.forEach { event ->
            try {
                sendEventToQueue(event)
                markAsProcessed(event)
            } catch (e: Exception) {
                handleEventFailure(event, e)
            }
        }
    }

    private fun sendEventToQueue(event: WithdrawalEventOutbox) {
    }

    private fun markAsProcessed(event: WithdrawalEventOutbox) {
        event.processedAt = Instant.now()
        outboxRepository.save(event)
    }

    private fun handleEventFailure(event: WithdrawalEventOutbox, exception: Exception) {
        event.retryCount++
        event.lastError = exception.message

        if (event.retryCount >= maxRetries) {
            event.processedAt = Instant.now()
        }
        
        outboxRepository.save(event)
    }
}

