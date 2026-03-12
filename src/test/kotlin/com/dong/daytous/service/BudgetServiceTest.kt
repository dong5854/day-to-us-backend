package com.dong.daytous.service

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BudgetServiceTest {

    @Mock
    lateinit var budgetEntryRepository: BudgetEntryRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var budgetService: BudgetService

    private lateinit var sharedSpace: SharedSpace
    private lateinit var user: User
    private val spaceId = UUID.randomUUID()
    private val email = "test@example.com"

    @BeforeEach
    fun setUp() {
        sharedSpace = SharedSpace(name = "Test Space").apply { id = spaceId }
        user = User(
            id = 1L,
            name = "Test User",
            email = email,
            role = Role.USER,
            provider = "google",
            providerId = "123",
            sharedSpace = sharedSpace,
        )
    }

    @Nested
    inner class GetAllBudgetEntriesForSpace {

        @Test
        fun `전체 예산 항목을 조회할 수 있다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            val entries = listOf(
                BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace),
                BudgetEntry(description = "점심", amount = 12000.0, date = LocalDate.of(2024, 1, 20), sharedSpace = sharedSpace),
            )
            whenever(budgetEntryRepository.findBySharedSpaceId(spaceId)).thenReturn(entries)

            val result = budgetService.getAllBudgetEntriesForSpace(spaceId, null, null, email)

            assertThat(result).hasSize(2)
        }

        @Test
        fun `연월 필터로 예산 항목을 조회할 수 있다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            val entries = listOf(
                BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 3, 15), sharedSpace = sharedSpace),
            )
            whenever(
                budgetEntryRepository.findBySharedSpaceIdAndDateBetween(
                    spaceId,
                    LocalDate.of(2024, 3, 1),
                    LocalDate.of(2024, 3, 31),
                ),
            ).thenReturn(entries)

            val result = budgetService.getAllBudgetEntriesForSpace(spaceId, 2024, 3, email)

            assertThat(result).hasSize(1)
            assertThat(result[0].description).isEqualTo("커피")
        }

        @Test
        fun `다른 공유 공간의 항목에 접근하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                budgetService.getAllBudgetEntriesForSpace(otherSpaceId, null, null, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }

        @Test
        fun `존재하지 않는 사용자로 조회하면 예외가 발생한다`() {
            whenever(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty())

            assertThatThrownBy {
                budgetService.getAllBudgetEntriesForSpace(spaceId, null, null, "unknown@test.com")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class GetBudgetEntryById {

        @Test
        fun `ID로 예산 항목을 조회할 수 있다`() {
            val entryId = UUID.randomUUID()
            val entry = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
                .apply { id = entryId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(budgetEntryRepository.findById(entryId)).thenReturn(Optional.of(entry))

            val result = budgetService.getBudgetEntryById(spaceId, entryId, email)

            assertThat(result.description).isEqualTo("커피")
            assertThat(result.amount).isEqualTo(5000.0)
        }

        @Test
        fun `존재하지 않는 항목 ID로 조회하면 예외가 발생한다`() {
            val entryId = UUID.randomUUID()
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(budgetEntryRepository.findById(entryId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                budgetService.getBudgetEntryById(spaceId, entryId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `다른 공간의 항목을 조회하면 예외가 발생한다`() {
            val entryId = UUID.randomUUID()
            val otherSpace = SharedSpace(name = "Other").apply { id = UUID.randomUUID() }
            val entry = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = otherSpace)
                .apply { id = entryId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(budgetEntryRepository.findById(entryId)).thenReturn(Optional.of(entry))

            assertThatThrownBy {
                budgetService.getBudgetEntryById(spaceId, entryId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class CreateBudgetEntry {

        @Test
        fun `예산 항목을 생성할 수 있다`() {
            val request = BudgetEntryRequest(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15))

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(budgetEntryRepository.save(any<BudgetEntry>())).thenAnswer { it.arguments[0] }

            val result = budgetService.createBudgetEntry(spaceId, request, email)

            assertThat(result.description).isEqualTo("커피")
            assertThat(result.amount).isEqualTo(5000.0)
            assertThat(result.date).isEqualTo(LocalDate.of(2024, 1, 15))
        }

        @Test
        fun `고정 지출 ID를 포함하여 생성할 수 있다`() {
            val fixedExpenseId = UUID.randomUUID()
            val request = BudgetEntryRequest(
                description = "월세",
                amount = 500000.0,
                date = LocalDate.of(2024, 1, 1),
                fixedExpenseId = fixedExpenseId,
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(budgetEntryRepository.save(any<BudgetEntry>())).thenAnswer { it.arguments[0] }

            val result = budgetService.createBudgetEntry(spaceId, request, email)

            assertThat(result.fixedExpenseId).isEqualTo(fixedExpenseId)
        }
    }

    @Nested
    inner class UpdateBudgetEntry {

        @Test
        fun `예산 항목을 수정할 수 있다`() {
            val entryId = UUID.randomUUID()
            val existing = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
                .apply { id = entryId }
            val request = BudgetEntryRequest(description = "라떼", amount = 6000.0, date = LocalDate.of(2024, 1, 15))

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(budgetEntryRepository.findById(entryId)).thenReturn(Optional.of(existing))
            whenever(budgetEntryRepository.save(any<BudgetEntry>())).thenAnswer { it.arguments[0] }

            val result = budgetService.updateBudgetEntry(spaceId, entryId, request, email)

            assertThat(result.description).isEqualTo("라떼")
            assertThat(result.amount).isEqualTo(6000.0)
        }
    }

    @Nested
    inner class DeleteBudgetEntry {

        @Test
        fun `예산 항목을 삭제할 수 있다`() {
            val entryId = UUID.randomUUID()
            val entry = BudgetEntry(description = "커피", amount = 5000.0, date = LocalDate.of(2024, 1, 15), sharedSpace = sharedSpace)
                .apply { id = entryId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(budgetEntryRepository.findById(entryId)).thenReturn(Optional.of(entry))

            budgetService.deleteBudgetEntry(spaceId, entryId, email)

            verify(budgetEntryRepository).delete(entry)
        }
    }
}
