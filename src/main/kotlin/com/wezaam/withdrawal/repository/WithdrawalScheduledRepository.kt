package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.WithdrawalScheduled
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface WithdrawalScheduledRepository : JpaRepository<WithdrawalScheduled, Long> {
    fun findAllByExecuteAtBefore(date: Instant): List<WithdrawalScheduled>
}

