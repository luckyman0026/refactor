package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.PaymentMethod
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentMethodRepository : JpaRepository<PaymentMethod, Long>

