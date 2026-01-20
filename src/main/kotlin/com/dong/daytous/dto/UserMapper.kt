package com.dong.daytous.dto

import com.dong.daytous.domain.user.User

fun User.toResponse(): UserResponse {
    return UserResponse(
        name = this.name,
        email = this.email
    )
}
