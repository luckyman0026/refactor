package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import org.springframework.data.jpa.repository.JpaRepository

interface WithdrawalEventOutboxRepository : JpaRepository<WithdrawalEventOutbox, Long> {
    fun findByProcessedAtIsNullOrderByCreatedAtAsc(): List<WithdrawalEventOutbox>
}

