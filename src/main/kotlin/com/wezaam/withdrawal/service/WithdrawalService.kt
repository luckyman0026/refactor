package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.exception.TransactionException
import com.wezaam.withdrawal.model.PaymentMethod
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class WithdrawalService @Autowired constructor(
    private val withdrawalRepository: WithdrawalRepository,
    private val withdrawalScheduledRepository: WithdrawalScheduledRepository,
    private val withdrawalProcessingService: WithdrawalProcessingService,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val eventsService: EventsService,
    private val transactionTemplate: TransactionTemplate
) {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    fun create(withdrawal: Withdrawal) {
        val pendingWithdrawal = withdrawalRepository.save(withdrawal)

        executorService.submit {
            val savedWithdrawalOptional = withdrawalRepository.findById(pendingWithdrawal.id!!)

            val paymentMethod = if (savedWithdrawalOptional.isPresent) {
                paymentMethodRepository.findById(savedWithdrawalOptional.get().paymentMethodId!!).orElse(null)
            } else {
                null
            }

            if (savedWithdrawalOptional.isPresent && paymentMethod != null) {
                val savedWithdrawal = savedWithdrawalOptional.get()
                // Use TransactionTemplate to ensure transactional execution in async context
                transactionTemplate.execute {
                    updateWithdrawalStatusAndPublishEvent(savedWithdrawal, paymentMethod, withdrawal.amount)
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

    private fun updateWithdrawalStatusAndPublishEvent(
        withdrawal: Withdrawal,
        paymentMethod: PaymentMethod,
        amount: Double?
    ) {
        try {
            val transactionId = withdrawalProcessingService.sendToProcessing(amount, paymentMethod)
            withdrawal.status = WithdrawalStatus.PROCESSING
            withdrawal.transactionId = transactionId
            withdrawalRepository.save(withdrawal)
            // Event is saved to outbox in the same transaction
            eventsService.send(withdrawal)
        } catch (e: Exception) {
            when (e) {
                is TransactionException -> {
                    withdrawal.status = WithdrawalStatus.FAILED
                    withdrawalRepository.save(withdrawal)
                    // Event is saved to outbox in the same transaction
                    eventsService.send(withdrawal)
                }
                else -> {
                    withdrawal.status = WithdrawalStatus.INTERNAL_ERROR
                    withdrawalRepository.save(withdrawal)
                    // Event is saved to outbox in the same transaction
                    eventsService.send(withdrawal)
                }
            }
        }
    }

    @Transactional
    private fun processScheduled(withdrawal: WithdrawalScheduled) {
        val paymentMethod = paymentMethodRepository.findById(withdrawal.paymentMethodId!!).orElse(null)
        if (paymentMethod != null) {
            try {
                val transactionId = withdrawalProcessingService.sendToProcessing(withdrawal.amount, paymentMethod)
                withdrawal.status = WithdrawalStatus.PROCESSING
                withdrawal.transactionId = transactionId
                withdrawalScheduledRepository.save(withdrawal)
                // Event is saved to outbox in the same transaction
                eventsService.send(withdrawal)
            } catch (e: Exception) {
                when (e) {
                    is TransactionException -> {
                        withdrawal.status = WithdrawalStatus.FAILED
                        withdrawalScheduledRepository.save(withdrawal)
                        // Event is saved to outbox in the same transaction
                        eventsService.send(withdrawal)
                    }
                    else -> {
                        withdrawal.status = WithdrawalStatus.INTERNAL_ERROR
                        withdrawalScheduledRepository.save(withdrawal)
                        // Event is saved to outbox in the same transaction
                        eventsService.send(withdrawal)
                    }
                }
            }
        }
    }
}

