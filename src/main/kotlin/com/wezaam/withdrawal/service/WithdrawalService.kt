package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.exception.TransactionException
import com.wezaam.withdrawal.model.PaymentMethod
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class WithdrawalService(
    private val withdrawalRepository: WithdrawalRepository,
    private val withdrawalScheduledRepository: WithdrawalScheduledRepository,
    private val withdrawalProcessingService: WithdrawalProcessingService,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val eventsService: EventsService
) {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    fun create(withdrawal: Withdrawal) {
        val pendingWithdrawal = withdrawalRepository.save(withdrawal)

        executorService.submit {
            processWithdrawal(pendingWithdrawal.id, withdrawal.amount)
        }
    }

    fun schedule(withdrawalScheduled: WithdrawalScheduled) {
        withdrawalScheduledRepository.save(withdrawalScheduled)
    }

    @Scheduled(fixedDelay = 5000)
    fun processScheduledWithdrawals() {
        withdrawalScheduledRepository.findAllByExecuteAtBefore(Instant.now())
            .forEach { processScheduled(it) }
    }

    private fun processWithdrawal(withdrawalId: Long?, amount: Double?) {
        val savedWithdrawal = withdrawalId?.let { withdrawalRepository.findById(it).orElse(null) } ?: return
        val paymentMethod = savedWithdrawal.paymentMethodId
            ?.let { paymentMethodRepository.findById(it).orElse(null) } ?: return

        try {
            val transactionId = withdrawalProcessingService.sendToProcessing(amount, paymentMethod)
            updateWithdrawalStatus(savedWithdrawal, WithdrawalStatus.PROCESSING, transactionId)
        } catch (e: Exception) {
            val status = determineErrorStatus(e)
            updateWithdrawalStatus(savedWithdrawal, status, null)
        }
    }

    private fun processScheduled(withdrawal: WithdrawalScheduled) {
        val paymentMethod = withdrawal.paymentMethodId
            ?.let { paymentMethodRepository.findById(it).orElse(null) } ?: return

        try {
            val transactionId = withdrawalProcessingService.sendToProcessing(withdrawal.amount, paymentMethod)
            updateScheduledWithdrawalStatus(withdrawal, WithdrawalStatus.PROCESSING, transactionId)
        } catch (e: Exception) {
            val status = determineErrorStatus(e)
            updateScheduledWithdrawalStatus(withdrawal, status, null)
        }
    }

    private fun updateWithdrawalStatus(
        withdrawal: Withdrawal,
        status: WithdrawalStatus,
        transactionId: Long?
    ) {
        withdrawal.status = status
        transactionId?.let { withdrawal.transactionId = it }
        val updatedWithdrawal = withdrawalRepository.save(withdrawal)
        eventsService.send(updatedWithdrawal)
    }

    private fun updateScheduledWithdrawalStatus(
        withdrawal: WithdrawalScheduled,
        status: WithdrawalStatus,
        transactionId: Long?
    ) {
        withdrawal.status = status
        transactionId?.let { withdrawal.transactionId = it }
        val updatedWithdrawal = withdrawalScheduledRepository.save(withdrawal)
        eventsService.send(updatedWithdrawal)
    }

    private fun determineErrorStatus(exception: Exception): WithdrawalStatus {
        return when (exception) {
            is TransactionException -> WithdrawalStatus.FAILED
            else -> WithdrawalStatus.INTERNAL_ERROR
        }
    }
}

