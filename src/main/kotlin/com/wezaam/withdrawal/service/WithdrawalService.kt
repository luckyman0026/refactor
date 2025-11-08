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
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class WithdrawalService @Autowired constructor(
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
            val savedWithdrawalOptional = withdrawalRepository.findById(pendingWithdrawal.id!!)

            val paymentMethod = if (savedWithdrawalOptional.isPresent) {
                paymentMethodRepository.findById(savedWithdrawalOptional.get().paymentMethodId!!).orElse(null)
            } else {
                null
            }

            if (savedWithdrawalOptional.isPresent && paymentMethod != null) {
                val savedWithdrawal = savedWithdrawalOptional.get()
                try {
                    val transactionId = withdrawalProcessingService.sendToProcessing(withdrawal.amount, paymentMethod)
                    savedWithdrawal.status = WithdrawalStatus.PROCESSING
                    savedWithdrawal.transactionId = transactionId
                    withdrawalRepository.save(savedWithdrawal)
                    eventsService.send(savedWithdrawal)
                } catch (e: Exception) {
                    when (e) {
                        is TransactionException -> {
                            savedWithdrawal.status = WithdrawalStatus.FAILED
                            withdrawalRepository.save(savedWithdrawal)
                            eventsService.send(savedWithdrawal)
                        }
                        else -> {
                            savedWithdrawal.status = WithdrawalStatus.INTERNAL_ERROR
                            withdrawalRepository.save(savedWithdrawal)
                            eventsService.send(savedWithdrawal)
                        }
                    }
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
        val paymentMethod = paymentMethodRepository.findById(withdrawal.paymentMethodId!!).orElse(null)
        if (paymentMethod != null) {
            try {
                val transactionId = withdrawalProcessingService.sendToProcessing(withdrawal.amount, paymentMethod)
                withdrawal.status = WithdrawalStatus.PROCESSING
                withdrawal.transactionId = transactionId
                withdrawalScheduledRepository.save(withdrawal)
                eventsService.send(withdrawal)
            } catch (e: Exception) {
                when (e) {
                    is TransactionException -> {
                        withdrawal.status = WithdrawalStatus.FAILED
                        withdrawalScheduledRepository.save(withdrawal)
                        eventsService.send(withdrawal)
                    }
                    else -> {
                        withdrawal.status = WithdrawalStatus.INTERNAL_ERROR
                        withdrawalScheduledRepository.save(withdrawal)
                        eventsService.send(withdrawal)
                    }
                }
            }
        }
    }
}

