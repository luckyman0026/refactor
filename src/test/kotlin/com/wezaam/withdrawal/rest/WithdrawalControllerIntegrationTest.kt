package com.wezaam.withdrawal.rest

import com.wezaam.withdrawal.model.PaymentMethod
import com.wezaam.withdrawal.model.User
import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import com.wezaam.withdrawal.model.WithdrawalStatus
import com.wezaam.withdrawal.repository.PaymentMethodRepository
import com.wezaam.withdrawal.repository.UserRepository
import com.wezaam.withdrawal.repository.WithdrawalRepository
import com.wezaam.withdrawal.repository.WithdrawalScheduledRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var paymentMethodRepository: PaymentMethodRepository

    @Autowired
    private lateinit var withdrawalRepository: WithdrawalRepository

    @Autowired
    private lateinit var withdrawalScheduledRepository: WithdrawalScheduledRepository

    private lateinit var testUser: User
    private lateinit var testPaymentMethod: PaymentMethod

    @BeforeEach
    fun setUp() {
        // Clean up
        withdrawalRepository.deleteAll()
        withdrawalScheduledRepository.deleteAll()
        paymentMethodRepository.deleteAll()
        userRepository.deleteAll()

        // Create test data
        testUser = userRepository.save(User(firstName = "Test User"))
        testPaymentMethod = paymentMethodRepository.save(
            PaymentMethod(user = testUser, name = "Test Payment Method")
        )
    }

    @Test
    fun `create withdrawal ASAP should return 200 and save withdrawal`() {
        // When & Then
        mockMvc.perform(
            post("/create-withdrawals")
                .param("userId", testUser.id.toString())
                .param("paymentMethodId", testPaymentMethod.id.toString())
                .param("amount", "100.0")
                .param("executeAt", "ASAP")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.amount").value(100.0))
            .andExpect(jsonPath("$.userId").value(testUser.id))

        // Verify withdrawal was saved
        val withdrawals = withdrawalRepository.findAll()
        kotlin.test.assertEquals(1, withdrawals.size)
        kotlin.test.assertEquals(WithdrawalStatus.PENDING, withdrawals[0].status)
    }

    @Test
    fun `create scheduled withdrawal should return 200 and save scheduled withdrawal`() {
        // Given
        val executeAt = Instant.now().plusSeconds(3600).toString()

        // When & Then
        mockMvc.perform(
            post("/create-withdrawals")
                .param("userId", testUser.id.toString())
                .param("paymentMethodId", testPaymentMethod.id.toString())
                .param("amount", "200.0")
                .param("executeAt", executeAt)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.amount").value(200.0))
            .andExpect(jsonPath("$.userId").value(testUser.id))
            .andExpect(jsonPath("$.executeAt").exists())

        // Verify scheduled withdrawal was saved
        val scheduledWithdrawals = withdrawalScheduledRepository.findAll()
        kotlin.test.assertEquals(1, scheduledWithdrawals.size)
        kotlin.test.assertEquals(WithdrawalStatus.PENDING, scheduledWithdrawals[0].status)
    }

    @Test
    fun `create withdrawal with missing parameters should return 400`() {
        mockMvc.perform(
            post("/create-withdrawals")
                .param("userId", testUser.id.toString())
                .param("amount", "100.0")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create withdrawal with invalid user should return 404`() {
        mockMvc.perform(
            post("/create-withdrawals")
                .param("userId", "99999")
                .param("paymentMethodId", testPaymentMethod.id.toString())
                .param("amount", "100.0")
                .param("executeAt", "ASAP")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `find all withdrawals should return both withdrawals and scheduled withdrawals`() {
        // Given
        withdrawalRepository.save(
            Withdrawal(
                userId = testUser.id,
                paymentMethodId = testPaymentMethod.id,
                amount = 100.0,
                createdAt = Instant.now(),
                status = WithdrawalStatus.PENDING
            )
        )
        withdrawalScheduledRepository.save(
            WithdrawalScheduled(
                userId = testUser.id,
                paymentMethodId = testPaymentMethod.id,
                amount = 200.0,
                createdAt = Instant.now(),
                executeAt = Instant.now().plusSeconds(3600),
                status = WithdrawalStatus.PENDING
            )
        )

        // When & Then
        mockMvc.perform(get("/find-all-withdrawals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
    }
}

