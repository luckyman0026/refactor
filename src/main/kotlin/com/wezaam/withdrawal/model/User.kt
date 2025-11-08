package com.wezaam.withdrawal.model

import javax.persistence.*

@Entity(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    var firstName: String? = null,
    
    @OneToMany(mappedBy = "user")
    var paymentMethods: List<PaymentMethod>? = null,
    
    var maxWithdrawalAmount: Double? = null
)