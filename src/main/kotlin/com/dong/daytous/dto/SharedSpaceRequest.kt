package com.dong.daytous.dto

import jakarta.validation.constraints.NotBlank

data class SharedSpaceRequest(
    @field:NotBlank(message = "이름은 비워둘 수 없습니다.")
    val name: String
)
