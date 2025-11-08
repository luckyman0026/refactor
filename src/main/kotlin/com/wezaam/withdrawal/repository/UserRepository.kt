package com.wezaam.withdrawal.repository

import com.wezaam.withdrawal.model.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>

