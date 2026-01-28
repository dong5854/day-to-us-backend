package com.dong.daytous.repository

import com.dong.daytous.domain.sharedspace.SharedSpace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

@DataJpaTest
class SharedSpaceRepositoryTest {
    @Autowired
    lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Test
    fun `SharedSpace를 저장하고 ID로 찾을 수 있다`() {
        // given
        val sharedSpace = SharedSpace(name = "Test Space")

        // when
        val savedSharedSpace = sharedSpaceRepository.save(sharedSpace)
        val foundSharedSpace = sharedSpaceRepository.findById(savedSharedSpace.id!!)

        // then
        assertThat(foundSharedSpace).isPresent
        assertThat(foundSharedSpace.get().id).isEqualTo(savedSharedSpace.id)
        assertThat(foundSharedSpace.get().name).isEqualTo(sharedSpace.name)
    }
}
