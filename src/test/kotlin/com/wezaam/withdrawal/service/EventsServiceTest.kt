package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.EventStatus
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.WithdrawalEventOutboxRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class EventsServiceTest {

    @Mock
    private lateinit var outboxRepository: WithdrawalEventOutboxRepository

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<WithdrawalEventOutbox>

    @InjectMocks
    private lateinit var eventsService: EventsService

    @BeforeEach
    fun setUp() {
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `send should save withdrawal event to outbox with PENDING status`() {
        // Given
        val withdrawal = Withdrawal(
            id = 1L,
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            status = WithdrawalStatus.PROCESSING,
            transactionId = 12345L
        )

        // When
        eventsService.send(withdrawal)

        // Then
        verify(outboxRepository).save(eventCaptor.capture())
        val event = eventCaptor.value

        assertEquals(1L, event.withdrawalId)
        assertEquals("WITHDRAWAL", event.withdrawalType)
        assertEquals("PROCESSING", event.status)
        assertEquals(100.0, event.amount)
        assertEquals(12345L, event.transactionId)
        assertEquals(EventStatus.PENDING, event.eventStatus)
    }

    @Test
    fun `send should save scheduled withdrawal event to outbox`() {
        // Given
        val scheduledWithdrawal = WithdrawalScheduled(
            id = 2L,
            userId = 1L,
            paymentMethodId = 1L,
            amount = 200.0,
            status = WithdrawalStatus.FAILED,
            transactionId = 67890L
        )

        // When
        eventsService.send(scheduledWithdrawal)

        // Then
        verify(outboxRepository).save(eventCaptor.capture())
        val event = eventCaptor.value

        assertEquals(2L, event.withdrawalId)
        assertEquals("WITHDRAWAL_SCHEDULED", event.withdrawalType)
        assertEquals("FAILED", event.status)
        assertEquals(200.0, event.amount)
        assertEquals(67890L, event.transactionId)
        assertEquals(EventStatus.PENDING, event.eventStatus)
    }
}

