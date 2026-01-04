package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BudgetServiceTest {

    @InjectMocks
    private lateinit var budgetService: BudgetService

    @Mock
    private lateinit var budgetEntryRepository: BudgetEntryRepository

    @Mock
    private lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Test
    fun `should get all budget entries for a space`() {
        // Given
        val spaceId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test Space")
        sharedSpace.id = spaceId // Set ID on the mock object
        val entries = listOf(
            BudgetEntry(description = "Entry 1", amount = 100.0, sharedSpace = sharedSpace),
            BudgetEntry(description = "Entry 2", amount = 200.0, sharedSpace = sharedSpace)
        )
        // Set IDs for mock return values after creation
        entries[0].id = UUID.randomUUID()
        entries[1].id = UUID.randomUUID()

        whenever(budgetEntryRepository.findBySharedSpaceId(spaceId)).doReturn(entries)

        // When
        val result = budgetService.getAllBudgetEntriesForSpace(spaceId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `should create a budget entry`() {
        // Given
        val spaceId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test Space")
        sharedSpace.id = spaceId // Set ID on the mock object
        val request = BudgetEntryRequest(description = "New Entry", amount = 150.0)
        val savedEntry = BudgetEntry(description = request.description, amount = request.amount, sharedSpace = sharedSpace)
        savedEntry.id = UUID.randomUUID() // Simulate Hibernate assigning ID

        whenever(sharedSpaceRepository.findById(spaceId)).doReturn(Optional.of(sharedSpace))
        whenever(budgetEntryRepository.save(any())).doReturn(savedEntry)

        // When
        val result = budgetService.createBudgetEntry(spaceId, request)

        // Then
        assertEquals(request.description, result.description)
        assertEquals(request.amount, result.amount)
        assertEquals(spaceId, result.sharedSpace?.id)
        assertEquals(savedEntry.id, result.id) // Assert that the ID was correctly returned
    }

    @Test
    fun `should delete a budget entry`() {
        // Given
        val spaceId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test Space")
        sharedSpace.id = spaceId // Set ID on the mock object
        val entry = BudgetEntry(description = "To be deleted", amount = 100.0, sharedSpace = sharedSpace)
        entry.id = entryId // Simulate Hibernate assigning ID

        whenever(budgetEntryRepository.findById(entryId)).doReturn(Optional.of(entry))

        // When
        val result = budgetService.deleteBudgetEntry(spaceId, entryId)

        // Then
        assertEquals(true, result)
        verify(budgetEntryRepository).deleteById(entryId)
    }

    @Test
    fun `should update a budget entry`() {
        // Given
        val spaceId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val sharedSpace = SharedSpace(name = "Test Space")
        sharedSpace.id = spaceId // Set ID on the mock object
        val existingEntry = BudgetEntry(description = "Old Entry", amount = 50.0, sharedSpace = sharedSpace)
        existingEntry.id = entryId // Simulate Hibernate assigning ID

        val updatedRequest = BudgetEntryRequest(description = "Updated Entry", amount = 75.0)
        val updatedEntry = existingEntry.copy(description = updatedRequest.description, amount = updatedRequest.amount)
        updatedEntry.id = entryId // Ensure ID is retained in copy

        whenever(budgetEntryRepository.findById(entryId)).doReturn(Optional.of(existingEntry))
        whenever(budgetEntryRepository.save(any())).doReturn(updatedEntry)

        // When
        val result = budgetService.updateBudgetEntry(spaceId, entryId, updatedRequest)

        // Then
        assertEquals(updatedRequest.description, result?.description)
        assertEquals(updatedRequest.amount, result?.amount)
        assertEquals(entryId, result?.id)
    }

    @Test
    fun `should return null when updating a non-existent budget entry`() {
        // Given
        val spaceId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val updatedRequest = BudgetEntryRequest(description = "Updated Entry", amount = 75.0)

        whenever(budgetEntryRepository.findById(entryId)).doReturn(Optional.empty())

        // When
        val result = budgetService.updateBudgetEntry(spaceId, entryId, updatedRequest)

        // Then
        assertEquals(null, result)
    }

    @Test
    fun `should return false when deleting a non-existent budget entry`() {
        // Given
        val spaceId = UUID.randomUUID()
        val entryId = UUID.randomUUID()

        whenever(budgetEntryRepository.findById(entryId)).doReturn(Optional.empty())

        // When
        val result = budgetService.deleteBudgetEntry(spaceId, entryId)

        // Then
        assertEquals(false, result)
    }
}
