package com.wezaam.withdrawal.rest

import com.wezaam.withdrawal.model.User
import com.wezaam.withdrawal.repository.UserRepository
import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Api
@RestController
class UserController(
    private val userRepository: UserRepository
) {

    @GetMapping("/find-all-users")
    fun findAll(): List<User> {
        return userRepository.findAll()
    }

    @GetMapping("/find-user-by-id/{id}")
    fun findById(@PathVariable id: Long): User {
        return userRepository.findById(id).orElseThrow()
    }
}

