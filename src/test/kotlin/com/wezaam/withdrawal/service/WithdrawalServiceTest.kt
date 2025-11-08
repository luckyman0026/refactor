package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.exception.TransactionException
import com.wezaam.withdrawal.model.PaymentMethod
import com.wezaam.withdrawal.model.User
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class WithdrawalServiceTest {

    @Mock
    private lateinit var withdrawalRepository: WithdrawalRepository

    @Mock
    private lateinit var withdrawalScheduledRepository: WithdrawalScheduledRepository

    @Mock
    private lateinit var withdrawalProcessingService: WithdrawalProcessingService

    @Mock
    private lateinit var paymentMethodRepository: PaymentMethodRepository

    @Mock
    private lateinit var eventsService: EventsService

    @Captor
    private lateinit var withdrawalCaptor: ArgumentCaptor<Withdrawal>

    @Captor
    private lateinit var scheduledWithdrawalCaptor: ArgumentCaptor<WithdrawalScheduled>

    @InjectMocks
    private lateinit var withdrawalService: WithdrawalService

    private lateinit var testPaymentMethod: PaymentMethod
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = User(id = 1L, firstName = "Test User")
        testPaymentMethod = PaymentMethod(
            id = 1L,
            user = testUser,
            name = "Test Payment Method"
        )
    }

    @Test
    fun `create should save withdrawal with PENDING status`() {
        // Given
        val withdrawal = Withdrawal(
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            status = WithdrawalStatus.PENDING
        )
        val savedWithdrawal = withdrawal.copy(id = 1L)
        
        whenever(withdrawalRepository.save(any())).thenReturn(savedWithdrawal)
        whenever(withdrawalRepository.findById(1L)).thenReturn(Optional.of(savedWithdrawal))
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any())).thenReturn(12345L)

        // When
        withdrawalService.create(withdrawal)

        // Then
        verify(withdrawalRepository).save(withdrawalCaptor.capture())
        assertEquals(WithdrawalStatus.PENDING, withdrawalCaptor.value.status)
        
        // Wait for async processing
        Thread.sleep(100)
        
        verify(withdrawalRepository, atLeastOnce()).findById(1L)
        verify(paymentMethodRepository).findById(1L)
    }

    @Test
    fun `create should update status to PROCESSING on successful transaction`() {
        // Given
        val withdrawal = Withdrawal(
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            status = WithdrawalStatus.PENDING
        )
        val savedWithdrawal = withdrawal.copy(id = 1L)
        val transactionId = 12345L
        
        whenever(withdrawalRepository.save(any())).thenReturn(savedWithdrawal)
        whenever(withdrawalRepository.findById(1L)).thenReturn(Optional.of(savedWithdrawal))
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any())).thenReturn(transactionId)

        // When
        withdrawalService.create(withdrawal)
        
        // Wait for async processing
        Thread.sleep(200)

        // Then
        verify(withdrawalProcessingService).sendToProcessing(100.0, testPaymentMethod)
        verify(withdrawalRepository, atLeast(2)).save(withdrawalCaptor.capture())
        val updatedWithdrawal = withdrawalCaptor.allValues.last()
        assertEquals(WithdrawalStatus.PROCESSING, updatedWithdrawal.status)
        assertEquals(transactionId, updatedWithdrawal.transactionId)
        verify(eventsService).send(any<Withdrawal>())
    }

    @Test
    fun `create should update status to FAILED on TransactionException`() {
        // Given
        val withdrawal = Withdrawal(
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            status = WithdrawalStatus.PENDING
        )
        val savedWithdrawal = withdrawal.copy(id = 1L)
        
        whenever(withdrawalRepository.save(any())).thenReturn(savedWithdrawal)
        whenever(withdrawalRepository.findById(1L)).thenReturn(Optional.of(savedWithdrawal))
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any()))
            .thenThrow(TransactionException("Transaction failed"))

        // When
        withdrawalService.create(withdrawal)
        
        // Wait for async processing
        Thread.sleep(200)

        // Then
        verify(withdrawalProcessingService).sendToProcessing(100.0, testPaymentMethod)
        verify(withdrawalRepository, atLeast(2)).save(withdrawalCaptor.capture())
        val updatedWithdrawal = withdrawalCaptor.allValues.last()
        assertEquals(WithdrawalStatus.FAILED, updatedWithdrawal.status)
        verify(eventsService).send(any<Withdrawal>())
    }

    @Test
    fun `create should update status to INTERNAL_ERROR on generic exception`() {
        // Given
        val withdrawal = Withdrawal(
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            status = WithdrawalStatus.PENDING
        )
        val savedWithdrawal = withdrawal.copy(id = 1L)
        
        whenever(withdrawalRepository.save(any())).thenReturn(savedWithdrawal)
        whenever(withdrawalRepository.findById(1L)).thenReturn(Optional.of(savedWithdrawal))
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any()))
            .thenThrow(RuntimeException("Internal error"))

        // When
        withdrawalService.create(withdrawal)
        
        // Wait for async processing
        Thread.sleep(200)

        // Then
        verify(withdrawalProcessingService).sendToProcessing(100.0, testPaymentMethod)
        verify(withdrawalRepository, atLeast(2)).save(withdrawalCaptor.capture())
        val updatedWithdrawal = withdrawalCaptor.allValues.last()
        assertEquals(WithdrawalStatus.INTERNAL_ERROR, updatedWithdrawal.status)
        verify(eventsService).send(any<Withdrawal>())
    }

    @Test
    fun `schedule should save scheduled withdrawal`() {
        // Given
        val scheduledWithdrawal = WithdrawalScheduled(
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            executeAt = Instant.now().plusSeconds(3600),
            status = WithdrawalStatus.PENDING
        )
        val savedScheduled = scheduledWithdrawal.copy(id = 1L)
        
        whenever(withdrawalScheduledRepository.save(any())).thenReturn(savedScheduled)

        // When
        withdrawalService.schedule(scheduledWithdrawal)

        // Then
        verify(withdrawalScheduledRepository).save(scheduledWithdrawalCaptor.capture())
        assertEquals(WithdrawalStatus.PENDING, scheduledWithdrawalCaptor.value.status)
        assertNotNull(scheduledWithdrawalCaptor.value.executeAt)
    }

    @Test
    fun `processScheduled should update status to PROCESSING on success`() {
        // Given
        val scheduledWithdrawal = WithdrawalScheduled(
            id = 1L,
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            executeAt = Instant.now().minusSeconds(100),
            status = WithdrawalStatus.PENDING
        )
        val transactionId = 12345L
        
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any())).thenReturn(transactionId)
        whenever(withdrawalScheduledRepository.save(any())).thenReturn(scheduledWithdrawal)

        // When
        withdrawalService.run() // This will process scheduled withdrawals
        withdrawalService.processScheduled(scheduledWithdrawal)

        // Then
        verify(withdrawalProcessingService).sendToProcessing(100.0, testPaymentMethod)
        verify(withdrawalScheduledRepository, atLeastOnce()).save(scheduledWithdrawalCaptor.capture())
        val updated = scheduledWithdrawalCaptor.allValues.last()
        assertEquals(WithdrawalStatus.PROCESSING, updated.status)
        assertEquals(transactionId, updated.transactionId)
        verify(eventsService).send(any<WithdrawalScheduled>())
    }

    @Test
    fun `processScheduled should update status to FAILED on TransactionException`() {
        // Given
        val scheduledWithdrawal = WithdrawalScheduled(
            id = 1L,
            userId = 1L,
            paymentMethodId = 1L,
            amount = 100.0,
            createdAt = Instant.now(),
            executeAt = Instant.now().minusSeconds(100),
            status = WithdrawalStatus.PENDING
        )
        
        whenever(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(testPaymentMethod))
        whenever(withdrawalProcessingService.sendToProcessing(any(), any()))
            .thenThrow(TransactionException("Transaction failed"))
        whenever(withdrawalScheduledRepository.save(any())).thenReturn(scheduledWithdrawal)

        // When
        withdrawalService.processScheduled(scheduledWithdrawal)

        // Then
        verify(withdrawalProcessingService).sendToProcessing(100.0, testPaymentMethod)
        verify(withdrawalScheduledRepository, atLeastOnce()).save(scheduledWithdrawalCaptor.capture())
        val updated = scheduledWithdrawalCaptor.allValues.last()
        assertEquals(WithdrawalStatus.FAILED, updated.status)
        verify(eventsService).send(any<WithdrawalScheduled>())
    }
}

