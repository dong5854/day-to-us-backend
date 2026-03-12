package com.dong.daytous.service

import com.dong.daytous.domain.fixedexpense.FixedExpense
import com.dong.daytous.domain.fixedexpense.Frequency
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.FixedExpenseRequest
import com.dong.daytous.repository.FixedExpenseRepository
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
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FixedExpenseServiceTest {

    @Mock
    lateinit var fixedExpenseRepository: FixedExpenseRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var fixedExpenseService: FixedExpenseService

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
    inner class CreateFixedExpense {

        @Test
        fun `고정 지출을 생성할 수 있다`() {
            val request = FixedExpenseRequest(
                description = "월세",
                amount = BigDecimal("500000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2024, 1, 1),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(fixedExpenseRepository.save(any<FixedExpense>())).thenAnswer {
                (it.arguments[0] as FixedExpense).apply { id = UUID.randomUUID() }
            }

            val result = fixedExpenseService.createFixedExpense(spaceId, request, email)

            assertThat(result.description).isEqualTo("월세")
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("500000"))
            assertThat(result.frequency).isEqualTo(Frequency.MONTHLY)
        }

        @Test
        fun `주간 고정 지출을 생성할 수 있다`() {
            val request = FixedExpenseRequest(
                description = "주간 장보기",
                amount = BigDecimal("100000"),
                frequency = Frequency.WEEKLY,
                startDate = LocalDate.of(2024, 1, 7),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(fixedExpenseRepository.save(any<FixedExpense>())).thenAnswer {
                (it.arguments[0] as FixedExpense).apply { id = UUID.randomUUID() }
            }

            val result = fixedExpenseService.createFixedExpense(spaceId, request, email)

            assertThat(result.frequency).isEqualTo(Frequency.WEEKLY)
        }

        @Test
        fun `연간 고정 지출을 생성할 수 있다`() {
            val request = FixedExpenseRequest(
                description = "보험료",
                amount = BigDecimal("1200000"),
                frequency = Frequency.YEARLY,
                startDate = LocalDate.of(2024, 6, 1),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(fixedExpenseRepository.save(any<FixedExpense>())).thenAnswer {
                (it.arguments[0] as FixedExpense).apply { id = UUID.randomUUID() }
            }

            val result = fixedExpenseService.createFixedExpense(spaceId, request, email)

            assertThat(result.frequency).isEqualTo(Frequency.YEARLY)
        }

        @Test
        fun `다른 공간에 고정 지출을 생성하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val request = FixedExpenseRequest(
                description = "월세",
                amount = BigDecimal("500000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2024, 1, 1),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                fixedExpenseService.createFixedExpense(otherSpaceId, request, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }

        @Test
        fun `존재하지 않는 공유 공간에 생성하면 예외가 발생한다`() {
            val request = FixedExpenseRequest(
                description = "월세",
                amount = BigDecimal("500000"),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2024, 1, 1),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                fixedExpenseService.createFixedExpense(spaceId, request, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class GetAllFixedExpenses {

        @Test
        fun `공유 공간의 모든 고정 지출을 조회할 수 있다`() {
            val expenses = listOf(
                FixedExpense(
                    description = "월세",
                    amount = BigDecimal("500000"),
                    frequency = Frequency.MONTHLY,
                    startDate = LocalDate.of(2024, 1, 1),
                    sharedSpace = sharedSpace,
                ).apply { id = UUID.randomUUID() },
                FixedExpense(
                    description = "보험",
                    amount = BigDecimal("100000"),
                    frequency = Frequency.YEARLY,
                    startDate = LocalDate.of(2024, 6, 1),
                    sharedSpace = sharedSpace,
                ).apply { id = UUID.randomUUID() },
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(fixedExpenseRepository.findBySharedSpaceId(spaceId)).thenReturn(expenses)

            val result = fixedExpenseService.getAllFixedExpenses(spaceId, email)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.description }).containsExactlyInAnyOrder("월세", "보험")
        }

        @Test
        fun `고정 지출이 없으면 빈 목록을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(fixedExpenseRepository.findBySharedSpaceId(spaceId)).thenReturn(emptyList())

            val result = fixedExpenseService.getAllFixedExpenses(spaceId, email)

            assertThat(result).isEmpty()
        }
    }
}
