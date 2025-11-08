package com.wezaam.withdrawal.service

import com.wezaam.withdrawal.model.Withdrawal
import com.wezaam.withdrawal.model.WithdrawalScheduled
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EventsService {

    @Async
    fun send(withdrawal: Withdrawal) {
    }

    @Async
    fun send(withdrawal: WithdrawalScheduled) {
    }
}

