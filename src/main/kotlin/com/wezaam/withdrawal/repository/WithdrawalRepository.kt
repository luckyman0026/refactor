package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.Withdrawal
import org.springframework.data.jpa.repository.JpaRepository

interface WithdrawalRepository : JpaRepository<Withdrawal, Long>

