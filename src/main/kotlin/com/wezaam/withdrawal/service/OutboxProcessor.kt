package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.EventStatus
import com.wezaam.withdrawal.repository.WithdrawalEventOutboxRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OutboxProcessor @Autowired constructor(
    private val outboxRepository: WithdrawalEventOutboxRepository,
    private val eventsService: EventsService
) {

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_SECONDS = 60L
    }

    /**
     * Processes pending events from the outbox every 5 seconds.
     * This ensures events are eventually sent even if the initial send failed.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processOutbox() {
        val pendingEvents = outboxRepository.findByEventStatusIn(
            listOf(EventStatus.PENDING, EventStatus.FAILED)
        )

        pendingEvents.forEach { event ->
            try {
                // Mark as processing
                event.eventStatus = EventStatus.PROCESSING
                outboxRepository.save(event)

                // Attempt to send
                eventsService.sendToQueue(event)

                // Mark as sent
                event.eventStatus = EventStatus.SENT
                event.processedAt = Instant.now()
                outboxRepository.save(event)
            } catch (e: Exception) {
                // Handle failure
                event.retryCount++
                event.errorMessage = e.message

                if (event.retryCount >= MAX_RETRY_COUNT) {
                    // Max retries reached, mark as failed permanently
                    event.eventStatus = EventStatus.FAILED
                } else {
                    // Will retry on next run
                    event.eventStatus = EventStatus.PENDING
                }
                outboxRepository.save(event)
            }
        }
    }
}

