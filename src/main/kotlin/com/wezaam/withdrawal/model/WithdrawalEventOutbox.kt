package com.wezaam.withdrawal.model

import javax.persistence.*
import java.time.Instant

@Entity(name = "withdrawal_event_outbox")
data class WithdrawalEventOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    val withdrawalId: Long? = null,
    val withdrawalType: String? = null, // "WITHDRAWAL" or "WITHDRAWAL_SCHEDULED"
    val status: String? = null,
    val amount: Double? = null,
    val transactionId: Long? = null,
    val userId: Long? = null,
    val paymentMethodId: Long? = null,
    
    @Enumerated(EnumType.STRING)
    var eventStatus: EventStatus = EventStatus.PENDING,
    
    var createdAt: Instant = Instant.now(),
    var processedAt: Instant? = null,
    var retryCount: Int = 0,
    var errorMessage: String? = null
)

enum class EventStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}

