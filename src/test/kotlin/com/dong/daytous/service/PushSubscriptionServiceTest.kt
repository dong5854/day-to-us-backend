package com.dong.daytous.service

import com.dong.daytous.domain.push.PushSubscription
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.PushKeys
import com.dong.daytous.dto.PushSubscribeRequest
import com.dong.daytous.repository.PushSubscriptionRepository
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
class PushSubscriptionServiceTest {

    @Mock
    lateinit var pushSubscriptionRepository: PushSubscriptionRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var pushSubscriptionService: PushSubscriptionService

    private lateinit var user: User
    private val email = "test@example.com"

    @BeforeEach
    fun setUp() {
        user = User(
            id = 1L,
            name = "Test User",
            email = email,
            role = Role.USER,
            provider = "google",
            providerId = "123",
        )
    }

    @Nested
    inner class Subscribe {

        @Test
        fun `새로운 구독을 생성한다`() {
            val request = PushSubscribeRequest(
                endpoint = "https://fcm.googleapis.com/fcm/send/test",
                keys = PushKeys(p256dh = "test-p256dh", auth = "test-auth"),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(pushSubscriptionRepository.findByUserIdAndEndpoint(user.id, request.endpoint))
                .thenReturn(null)
            whenever(pushSubscriptionRepository.save(any<PushSubscription>())).thenAnswer {
                (it.arguments[0] as PushSubscription).apply { id = UUID.randomUUID() }
            }

            val result = pushSubscriptionService.subscribe(email, request)

            assertThat(result.endpoint).isEqualTo(request.endpoint)
            assertThat(result.id).isNotNull()
        }

        @Test
        fun `동일 endpoint가 존재하면 업데이트한다`() {
            val request = PushSubscribeRequest(
                endpoint = "https://fcm.googleapis.com/fcm/send/test",
                keys = PushKeys(p256dh = "new-p256dh", auth = "new-auth"),
            )
            val existingId = UUID.randomUUID()
            val existing = PushSubscription(
                user = user,
                endpoint = request.endpoint,
                p256dh = "old-p256dh",
                auth = "old-auth",
            ).apply { id = existingId }

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(pushSubscriptionRepository.findByUserIdAndEndpoint(user.id, request.endpoint))
                .thenReturn(existing)
            whenever(pushSubscriptionRepository.save(any<PushSubscription>())).thenAnswer {
                it.arguments[0] as PushSubscription
            }

            val result = pushSubscriptionService.subscribe(email, request)

            assertThat(result.id).isEqualTo(existingId)
        }

        @Test
        fun `존재하지 않는 유저로 구독하면 예외가 발생한다`() {
            val request = PushSubscribeRequest(
                endpoint = "https://fcm.googleapis.com/fcm/send/test",
                keys = PushKeys(p256dh = "test-p256dh", auth = "test-auth"),
            )

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.empty())

            assertThatThrownBy {
                pushSubscriptionService.subscribe(email, request)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class Unsubscribe {

        @Test
        fun `구독을 해제한다`() {
            val endpoint = "https://fcm.googleapis.com/fcm/send/test"

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            pushSubscriptionService.unsubscribe(email, endpoint)

            verify(pushSubscriptionRepository).deleteByUserIdAndEndpoint(user.id, endpoint)
        }

        @Test
        fun `존재하지 않는 유저로 해제하면 예외가 발생한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.empty())

            assertThatThrownBy {
                pushSubscriptionService.unsubscribe(email, "any-endpoint")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }
}
