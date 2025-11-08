package com.wezaam.withdrawal.model

import java.time.Instant
import javax.persistence.*

@Entity(name = "withdrawals")
data class Withdrawal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    var transactionId: Long? = null,
    var amount: Double? = null,
    var createdAt: Instant? = null,
    var userId: Long? = null,
    var paymentMethodId: Long? = null,
    
    @Enumerated(EnumType.STRING)
    var status: WithdrawalStatus? = null
)