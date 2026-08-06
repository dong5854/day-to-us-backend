package com.dong.daytous.service

import com.dong.daytous.domain.category.ExpenseCategory
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.ExpenseCategoryRequest
import com.dong.daytous.repository.ExpenseCategoryRepository
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ExpenseCategoryServiceTest {

    @Mock
    lateinit var expenseCategoryRepository: ExpenseCategoryRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var expenseCategoryService: ExpenseCategoryService

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
    inner class CreateCategory {

        @Test
        fun `카테고리를 생성할 수 있다`() {
            val request = ExpenseCategoryRequest(name = "식비")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(expenseCategoryRepository.save(any<ExpenseCategory>())).thenAnswer {
                (it.arguments[0] as ExpenseCategory).apply { id = UUID.randomUUID() }
            }

            val result = expenseCategoryService.createCategory(spaceId, request, email)

            assertThat(result.name).isEqualTo("식비")
            assertThat(result.id).isNotNull()
        }

        @Test
        fun `다른 공간에 카테고리를 생성하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val request = ExpenseCategoryRequest(name = "식비")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                expenseCategoryService.createCategory(otherSpaceId, request, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }

        @Test
        fun `존재하지 않는 공유 공간에 생성하면 예외가 발생한다`() {
            val request = ExpenseCategoryRequest(name = "식비")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                expenseCategoryService.createCategory(spaceId, request, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `존재하지 않는 사용자로 생성하면 예외가 발생한다`() {
            val request = ExpenseCategoryRequest(name = "식비")

            whenever(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty())

            assertThatThrownBy {
                expenseCategoryService.createCategory(spaceId, request, "unknown@test.com")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class GetAllCategories {

        @Test
        fun `카테고리 목록을 조회할 수 있다`() {
            val categories = listOf(
                ExpenseCategory(name = "식비", sharedSpace = sharedSpace).apply { id = UUID.randomUUID() },
                ExpenseCategory(name = "교통비", sharedSpace = sharedSpace).apply { id = UUID.randomUUID() },
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(expenseCategoryRepository.findBySharedSpaceId(spaceId)).thenReturn(categories)

            val result = expenseCategoryService.getAllCategories(spaceId, email)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("식비", "교통비")
        }

        @Test
        fun `카테고리가 없으면 빈 목록을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(expenseCategoryRepository.findBySharedSpaceId(spaceId)).thenReturn(emptyList())

            val result = expenseCategoryService.getAllCategories(spaceId, email)

            assertThat(result).isEmpty()
        }

        @Test
        fun `다른 공간의 카테고리에 접근하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                expenseCategoryService.getAllCategories(otherSpaceId, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }
    }

    @Nested
    inner class DeleteCategory {

        @Test
        fun `카테고리를 삭제할 수 있다`() {
            val categoryId = UUID.randomUUID()
            val category = ExpenseCategory(name = "식비", sharedSpace = sharedSpace).apply { id = categoryId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(expenseCategoryRepository.findByIdAndSharedSpaceId(categoryId, spaceId)).thenReturn(category)

            expenseCategoryService.deleteCategory(spaceId, categoryId, email)

            verify(expenseCategoryRepository).delete(category)
        }

        @Test
        fun `존재하지 않는 카테고리를 삭제하면 예외가 발생한다`() {
            val categoryId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(expenseCategoryRepository.findByIdAndSharedSpaceId(categoryId, spaceId)).thenReturn(null)

            assertThatThrownBy {
                expenseCategoryService.deleteCategory(spaceId, categoryId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `다른 공간의 카테고리를 삭제하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val categoryId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                expenseCategoryService.deleteCategory(otherSpaceId, categoryId, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }
    }
}
