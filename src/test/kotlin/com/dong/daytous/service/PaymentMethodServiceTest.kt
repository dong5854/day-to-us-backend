package com.dong.daytous.service

import com.dong.daytous.domain.paymentmethod.PaymentMethod
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.PaymentMethodRequest
import com.dong.daytous.repository.PaymentMethodRepository
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
class PaymentMethodServiceTest {

    @Mock
    lateinit var paymentMethodRepository: PaymentMethodRepository

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var budgetEntryRepository: com.dong.daytous.repository.BudgetEntryRepository

    @InjectMocks
    lateinit var paymentMethodService: PaymentMethodService

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
    inner class CreatePaymentMethod {

        @Test
        fun `결제 수단을 생성할 수 있다`() {
            val request = PaymentMethodRequest(name = "신용카드")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.of(sharedSpace))
            whenever(paymentMethodRepository.save(any<PaymentMethod>())).thenAnswer {
                (it.arguments[0] as PaymentMethod).apply { id = UUID.randomUUID() }
            }

            val result = paymentMethodService.createPaymentMethod(spaceId, request, email)

            assertThat(result.name).isEqualTo("신용카드")
            assertThat(result.id).isNotNull()
        }

        @Test
        fun `다른 공간에 결제 수단을 생성하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val request = PaymentMethodRequest(name = "신용카드")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                paymentMethodService.createPaymentMethod(otherSpaceId, request, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }

        @Test
        fun `존재하지 않는 공유 공간에 생성하면 예외가 발생한다`() {
            val request = PaymentMethodRequest(name = "신용카드")

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findById(spaceId)).thenReturn(Optional.empty())

            assertThatThrownBy {
                paymentMethodService.createPaymentMethod(spaceId, request, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `존재하지 않는 사용자로 생성하면 예외가 발생한다`() {
            val request = PaymentMethodRequest(name = "신용카드")

            whenever(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty())

            assertThatThrownBy {
                paymentMethodService.createPaymentMethod(spaceId, request, "unknown@test.com")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class GetAllPaymentMethods {

        @Test
        fun `결제 수단 목록을 조회할 수 있다`() {
            val methods = listOf(
                PaymentMethod(name = "신용카드", sharedSpace = sharedSpace).apply { id = UUID.randomUUID() },
                PaymentMethod(name = "현금", sharedSpace = sharedSpace).apply { id = UUID.randomUUID() },
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(paymentMethodRepository.findBySharedSpaceId(spaceId)).thenReturn(methods)

            val result = paymentMethodService.getAllPaymentMethods(spaceId, email)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("신용카드", "현금")
        }

        @Test
        fun `결제 수단이 없으면 빈 목록을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(paymentMethodRepository.findBySharedSpaceId(spaceId)).thenReturn(emptyList())

            val result = paymentMethodService.getAllPaymentMethods(spaceId, email)

            assertThat(result).isEmpty()
        }

        @Test
        fun `다른 공간의 결제 수단에 접근하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                paymentMethodService.getAllPaymentMethods(otherSpaceId, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }
    }

    @Nested
    inner class DeletePaymentMethod {

        @Test
        fun `결제 수단을 삭제할 수 있다`() {
            val methodId = UUID.randomUUID()
            val method = PaymentMethod(name = "신용카드", sharedSpace = sharedSpace).apply { id = methodId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(paymentMethodRepository.findByIdAndSharedSpaceId(methodId, spaceId)).thenReturn(method)

            paymentMethodService.deletePaymentMethod(spaceId, methodId, email)

            verify(paymentMethodRepository).delete(method)
        }

        @Test
        fun `존재하지 않는 결제 수단을 삭제하면 예외가 발생한다`() {
            val methodId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(paymentMethodRepository.findByIdAndSharedSpaceId(methodId, spaceId)).thenReturn(null)

            assertThatThrownBy {
                paymentMethodService.deletePaymentMethod(spaceId, methodId, email)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `다른 공간의 결제 수단을 삭제하면 예외가 발생한다`() {
            val otherSpaceId = UUID.randomUUID()
            val methodId = UUID.randomUUID()

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                paymentMethodService.deletePaymentMethod(otherSpaceId, methodId, email)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Access denied")
        }
    }
}
