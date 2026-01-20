package com.dong.daytous.dto

import com.dong.daytous.domain.sharedspace.SharedSpace

fun SharedSpace.toResponse(): SharedSpaceResponse {
    return SharedSpaceResponse(
        id = this.id!!,
        name = this.name,
        inviteCode = this.inviteCode
    )
}
