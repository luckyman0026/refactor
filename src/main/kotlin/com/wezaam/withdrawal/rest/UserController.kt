package com.wezaam.withdrawal.rest

import com.wezaam.withdrawal.model.User
import com.wezaam.withdrawal.repository.UserRepository
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Api
@RestController
class UserController @Autowired constructor(
    private val context: ApplicationContext
) {

    @GetMapping("/find-all-users")
    fun findAll(): List<User> {
        return context.getBean(UserRepository::class.java).findAll()
    }

    @GetMapping("/find-user-by-id/{id}")
    fun findById(@PathVariable id: Long): User {
        return context.getBean(UserRepository::class.java).findById(id).orElseThrow()
    }
}
