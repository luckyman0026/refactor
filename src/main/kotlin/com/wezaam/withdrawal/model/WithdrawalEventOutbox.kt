package com.wezaam.withdrawal.model

import java.time.Instant
import javax.persistence.*

@Entity(name = "withdrawal_event_outbox")
data class WithdrawalEventOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    var withdrawalId: Long? = null,
    var withdrawalType: String? = null,
    var status: String? = null,
    var transactionId: Long? = null,
    var amount: Double? = null,
    var userId: Long? = null,
    var paymentMethodId: Long? = null,
    var createdAt: Instant? = null,
    var processedAt: Instant? = null,
    var retryCount: Int = 0,
    var lastError: String? = null
)

