package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.repository.SharedSpaceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SharedSpaceServiceTest {

    @InjectMocks
    private lateinit var sharedSpaceService: SharedSpaceService

    @Mock
    private lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Test
    fun `should create a shared space`() {
        // Given
        val spaceName = "Test Space"
        val expectedSpace = SharedSpace(name = spaceName)
        expectedSpace.id = UUID.randomUUID() // Simulate Hibernate assigning ID
        whenever(sharedSpaceRepository.save(any())).doReturn(expectedSpace)

        // When
        val result = sharedSpaceService.createSharedSpace(spaceName)

        // Then
        assertEquals(expectedSpace.id, result.id)
        assertEquals(expectedSpace.name, result.name)
    }

    @Test
    fun `should return all shared spaces`() {
        // Given
        val space1 = SharedSpace(name = "Space 1")
        space1.id = UUID.randomUUID()
        val space2 = SharedSpace(name = "Space 2")
        space2.id = UUID.randomUUID()
        val spaces = listOf(space1, space2)
        whenever(sharedSpaceRepository.findAll()).doReturn(spaces)

        // When
        val result = sharedSpaceService.getAllSharedSpaces()

        // Then
        assertEquals(2, result.size)
        assertEquals("Space 1", result[0].name)
    }
}
