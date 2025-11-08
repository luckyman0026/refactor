package com.wezaam.withdrawal.rest

import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import com.wezaam.withdrawal.service.WithdrawalService
import io.swagger.annotations.Api
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Api
@RestController
class WithdrawalController(
    private val withdrawalService: WithdrawalService,
    private val withdrawalRepository: WithdrawalRepository,
    private val withdrawalScheduledRepository: WithdrawalScheduledRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val userController: UserController
) {

    @PostMapping("/create-withdrawals")
    fun create(
        @RequestParam userId: String?,
        @RequestParam paymentMethodId: String?,
        @RequestParam amount: String?,
        @RequestParam executeAt: String?
    ): ResponseEntity<*> {
        if (userId.isNullOrBlank() || paymentMethodId.isNullOrBlank() || 
            amount.isNullOrBlank() || executeAt.isNullOrBlank()) {
            return ResponseEntity("Required params are missing", HttpStatus.BAD_REQUEST)
        }

        val userIdLong = userId.toLongOrNull() ?: return ResponseEntity("Invalid userId", HttpStatus.BAD_REQUEST)
        val paymentMethodIdLong = paymentMethodId.toLongOrNull() 
            ?: return ResponseEntity("Invalid paymentMethodId", HttpStatus.BAD_REQUEST)
        val amountDouble = amount.toDoubleOrNull() 
            ?: return ResponseEntity("Invalid amount", HttpStatus.BAD_REQUEST)

        try {
            userController.findById(userIdLong)
        } catch (e: Exception) {
            return ResponseEntity("User not found", HttpStatus.NOT_FOUND)
        }

        if (!paymentMethodRepository.existsById(paymentMethodIdLong)) {
            return ResponseEntity("Payment method not found", HttpStatus.NOT_FOUND)
        }

        val result = if (executeAt == "ASAP") {
            createImmediateWithdrawal(userIdLong, paymentMethodIdLong, amountDouble)
        } else {
            createScheduledWithdrawal(userIdLong, paymentMethodIdLong, amountDouble, executeAt)
        }

        return ResponseEntity(result, HttpStatus.OK)
    }

    @GetMapping("/find-all-withdrawals")
    fun findAll(): ResponseEntity<List<*>> {
        val withdrawals = withdrawalRepository.findAll()
        val scheduledWithdrawals = withdrawalScheduledRepository.findAll()
        return ResponseEntity(withdrawals + scheduledWithdrawals, HttpStatus.OK)
    }

    private fun createImmediateWithdrawal(
        userId: Long,
        paymentMethodId: Long,
        amount: Double
    ): Withdrawal {
        val withdrawal = Withdrawal(
            userId = userId,
            paymentMethodId = paymentMethodId,
            amount = amount,
            createdAt = Instant.now(),
            status = WithdrawalStatus.PENDING
        )
        withdrawalService.create(withdrawal)
        return withdrawal
    }

    private fun createScheduledWithdrawal(
        userId: Long,
        paymentMethodId: Long,
        amount: Double,
        executeAt: String
    ): WithdrawalScheduled {
        val executeAtInstant = try {
            Instant.parse(executeAt)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid executeAt format", e)
        }

        val withdrawalScheduled = WithdrawalScheduled(
            userId = userId,
            paymentMethodId = paymentMethodId,
            amount = amount,
            createdAt = Instant.now(),
            executeAt = executeAtInstant,
            status = WithdrawalStatus.PENDING
        )
        withdrawalService.schedule(withdrawalScheduled)
        return withdrawalScheduled
    }
}

