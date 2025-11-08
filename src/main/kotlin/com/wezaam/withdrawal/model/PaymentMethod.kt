package com.wezaam.withdrawal.model

import com.fasterxml.jackson.annotation.JsonIgnore
import javax.persistence.*

@Entity(name = "payment_methods")
data class PaymentMethod(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JsonIgnore
    var user: User? = null,

    var name:String? = null
)