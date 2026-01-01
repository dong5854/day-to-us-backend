package com.dong.daytous.repository

import com.dong.daytous.domain.sharedspace.SharedSpace
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SharedSpaceRepository : JpaRepository<SharedSpace, Long>
