package com.wezaam.withdrawal.rest

import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import com.wezaam.withdrawal.service.WithdrawalService
import io.swagger.annotations.Api
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Api
@RestController
class WithdrawalController(
    private val context: ApplicationContext,
    private val userController: UserController
) {

    @PostMapping("/create-withdrawals")
    fun create(request: javax.servlet.http.HttpServletRequest): ResponseEntity<*> {
        val userId = request.getParameter("userId")
        val paymentMethodId = request.getParameter("paymentMethodId")
        val amount = request.getParameter("amount")
        val executeAt = request.getParameter("executeAt")

        if (userId == null || paymentMethodId == null || amount == null || executeAt == null) {
            return ResponseEntity("Required params are missing", HttpStatus.BAD_REQUEST)
        }

        try {
            userController.findById(userId.toLong())
        } catch (e: Exception) {
            return ResponseEntity("User not found", HttpStatus.NOT_FOUND)
        }

        val paymentMethodRepo = context.getBean(PaymentMethodRepository::class.java)
        if (!paymentMethodRepo.findById(paymentMethodId.toLong()).isPresent) {
            return ResponseEntity("Payment method not found", HttpStatus.NOT_FOUND)
        }

        val withdrawalService = context.getBean(WithdrawalService::class.java)
        val body: Any = if (executeAt == "ASAP") {
            val withdrawal = Withdrawal(
                userId = userId.toLong(),
                paymentMethodId = paymentMethodId.toLong(),
                amount = amount.toDouble(),
                createdAt = Instant.now(),
                status = WithdrawalStatus.PENDING
            )
            withdrawalService.create(withdrawal)
            withdrawal
        } else {
            val withdrawalScheduled = WithdrawalScheduled(
                userId = userId.toLong(),
                paymentMethodId = paymentMethodId.toLong(),
                amount = amount.toDouble(),
                createdAt = Instant.now(),
                executeAt = Instant.parse(executeAt),
                status = WithdrawalStatus.PENDING
            )
            withdrawalService.schedule(withdrawalScheduled)
            withdrawalScheduled
        }

        return ResponseEntity(body, HttpStatus.OK)
    }

    @GetMapping("/find-all-withdrawals")
    fun findAll(): ResponseEntity<*> {
        val withdrawals = context.getBean(WithdrawalRepository::class.java).findAll()
        val withdrawalsScheduled = context.getBean(WithdrawalScheduledRepository::class.java).findAll()
        val result = mutableListOf<Any>()
        result.addAll(withdrawals)
        result.addAll(withdrawalsScheduled)

        return ResponseEntity(result, HttpStatus.OK)
    }
}

