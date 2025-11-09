package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.EventStatus
import com.wezaam.withdrawal.model.WithdrawalEventOutbox
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class OutboxProcessorTest {

    @Mock
    private lateinit var outboxRepository: WithdrawalEventOutboxRepository

    @Mock
    private lateinit var eventsService: EventsService

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<WithdrawalEventOutbox>

    @InjectMocks
    private lateinit var outboxProcessor: OutboxProcessor

    @BeforeEach
    fun setUp() {
        whenever(outboxRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `processOutbox should mark event as SENT after successful processing`() {
        // Given
        val event = WithdrawalEventOutbox(
            id = 1L,
            withdrawalId = 1L,
            withdrawalType = "WITHDRAWAL",
            status = "PROCESSING",
            eventStatus = EventStatus.PENDING
        )
        whenever(outboxRepository.findByEventStatusIn(any())).thenReturn(listOf(event))

        // When
        outboxProcessor.processOutbox()

        // Then
        verify(eventsService).sendToQueue(any())
        verify(outboxRepository, org.mockito.kotlin.atLeast(2)).save(eventCaptor.capture())
        val savedEvents = eventCaptor.allValues
        
        // First save should be PROCESSING status
        assertEquals(EventStatus.PROCESSING, savedEvents[0].eventStatus)
        
        // Second save should be SENT status
        assertEquals(EventStatus.SENT, savedEvents[1].eventStatus)
        assertEquals(0, savedEvents[1].retryCount)
        assertEquals(null, savedEvents[1].errorMessage)
    }

    @Test
    fun `processOutbox should retry failed events`() {
        // Given
        val event = WithdrawalEventOutbox(
            id = 1L,
            withdrawalId = 1L,
            withdrawalType = "WITHDRAWAL",
            status = "PROCESSING",
            eventStatus = EventStatus.PENDING,
            retryCount = 0
        )
        whenever(outboxRepository.findByEventStatusIn(any())).thenReturn(listOf(event))
        doThrow(RuntimeException("Connection failed")).whenever(eventsService).sendToQueue(any())

        // When
        outboxProcessor.processOutbox()

        // Then
        verify(eventsService).sendToQueue(any())
        verify(outboxRepository, org.mockito.kotlin.atLeast(2)).save(eventCaptor.capture())
        val savedEvents = eventCaptor.allValues
        
        // Event should be marked as PENDING for retry
        val retriedEvent = savedEvents.last()
        assertEquals(EventStatus.PENDING, retriedEvent.eventStatus)
        assertEquals(1, retriedEvent.retryCount)
        assertEquals("Connection failed", retriedEvent.errorMessage)
    }

    @Test
    fun `processOutbox should mark event as FAILED after max retries`() {
        // Given
        val event = WithdrawalEventOutbox(
            id = 1L,
            withdrawalId = 1L,
            withdrawalType = "WITHDRAWAL",
            status = "PROCESSING",
            eventStatus = EventStatus.PENDING,
            retryCount = 3 // Already at max retries
        )
        whenever(outboxRepository.findByEventStatusIn(any())).thenReturn(listOf(event))
        doThrow(RuntimeException("Connection failed")).whenever(eventsService).sendToQueue(any())

        // When
        outboxProcessor.processOutbox()

        // Then
        verify(eventsService).sendToQueue(any())
        verify(outboxRepository, org.mockito.kotlin.atLeast(2)).save(eventCaptor.capture())
        val savedEvents = eventCaptor.allValues
        
        // Event should be marked as FAILED after max retries
        val failedEvent = savedEvents.last()
        assertEquals(EventStatus.FAILED, failedEvent.eventStatus)
        assertEquals(4, failedEvent.retryCount) // Incremented to 4
    }

    @Test
    fun `processOutbox should process both PENDING and FAILED events`() {
        // Given
        val pendingEvent = WithdrawalEventOutbox(
            id = 1L,
            withdrawalId = 1L,
            eventStatus = EventStatus.PENDING
        )
        val failedEvent = WithdrawalEventOutbox(
            id = 2L,
            withdrawalId = 2L,
            eventStatus = EventStatus.FAILED,
            retryCount = 1
        )
        whenever(outboxRepository.findByEventStatusIn(any())).thenReturn(listOf(pendingEvent, failedEvent))

        // When
        outboxProcessor.processOutbox()

        // Then
        verify(eventsService, org.mockito.kotlin.times(2)).sendToQueue(any())
    }
}

