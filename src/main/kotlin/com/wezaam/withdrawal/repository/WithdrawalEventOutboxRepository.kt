package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.EventStatus
import com.wezaam.withdrawal.model.WithdrawalEventOutbox
import org.springframework.data.jpa.repository.JpaRepository

interface WithdrawalEventOutboxRepository : JpaRepository<WithdrawalEventOutbox, Long> {
    fun findByEventStatus(eventStatus: EventStatus): List<WithdrawalEventOutbox>
    fun findByEventStatusIn(eventStatuses: List<EventStatus>): List<WithdrawalEventOutbox>
}

