package com.dong.daytous.dto

import java.util.UUID

data class SharedSpaceResponse(
    val id: UUID,
    val name: String,
    val inviteCode: String,
)
