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
            val savedWithdrawal = withdrawalRepository.findById(pendingWithdrawal.id)
            val paymentMethod = savedWithdrawal
                ?.let { paymentMethodRepository.findById(it.paymentMethodId).orElse(null) }

            if (savedWithdrawal != null && paymentMethod != null) {
                try {
                    val transactionId = withdrawalProcessingService.sendToProcessing(
                        withdrawal.amount,
                        paymentMethod
                    )
                    savedWithdrawal.status = WithdrawalStatus.PROCESSING
                    savedWithdrawal.transactionId = transactionId
                    val updatedWithdrawal = withdrawalRepository.save(savedWithdrawal)
                    eventsService.send(updatedWithdrawal)
                } catch (e: Exception) {
                    val status = when (e) {
                        is TransactionException -> WithdrawalStatus.FAILED
                        else -> WithdrawalStatus.INTERNAL_ERROR
                    }
                    savedWithdrawal.status = status
                    val errorWithdrawal = withdrawalRepository.save(savedWithdrawal)
                    eventsService.send(errorWithdrawal)
                }
            }
        }
    }

    fun schedule(withdrawalScheduled: WithdrawalScheduled) {
        withdrawalScheduledRepository.save(withdrawalScheduled)
    }

    @Scheduled(fixedDelay = 5000)
    fun run() {
        withdrawalScheduledRepository.findAllByExecuteAtBefore(Instant.now())
            .forEach { processScheduled(it) }
    }

    private fun processScheduled(withdrawal: WithdrawalScheduled) {
        val paymentMethod = paymentMethodRepository.findById(withdrawal.paymentMethodId).orElse(null)
        if (paymentMethod != null) {
            try {
                val transactionId = withdrawalProcessingService.sendToProcessing(
                    withdrawal.amount,
                    paymentMethod
                )
                withdrawal.status = WithdrawalStatus.PROCESSING
                withdrawal.transactionId = transactionId
                val updatedWithdrawal = withdrawalScheduledRepository.save(withdrawal)
                eventsService.send(updatedWithdrawal)
            } catch (e: Exception) {
                val status = when (e) {
                    is TransactionException -> WithdrawalStatus.FAILED
                    else -> WithdrawalStatus.INTERNAL_ERROR
                }
                withdrawal.status = status
                val errorWithdrawal = withdrawalScheduledRepository.save(withdrawal)
                eventsService.send(errorWithdrawal)
            }
        }
    }
}

