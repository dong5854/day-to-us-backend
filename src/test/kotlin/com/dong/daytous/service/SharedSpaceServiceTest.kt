package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SharedSpaceServiceTest {

    @Mock
    lateinit var sharedSpaceRepository: SharedSpaceRepository

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var sharedSpaceService: SharedSpaceService

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
    inner class CreateSharedSpace {

        @Test
        fun `공유 공간을 생성할 수 있다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.save(any<SharedSpace>())).thenAnswer {
                (it.arguments[0] as SharedSpace).apply { id = UUID.randomUUID() }
            }

            val result = sharedSpaceService.createSharedSpace("우리 공간", email)

            assertThat(result.name).isEqualTo("우리 공간")
            assertThat(result.inviteCode).isNotBlank()
        }

        @Test
        fun `이미 공유 공간에 속한 사용자가 생성하면 예외가 발생한다`() {
            val existingSpace = SharedSpace(name = "기존 공간").apply { id = UUID.randomUUID() }
            user.sharedSpace = existingSpace
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                sharedSpaceService.createSharedSpace("새 공간", email)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("already belongs to a shared space")
        }

        @Test
        fun `존재하지 않는 사용자로 생성하면 예외가 발생한다`() {
            whenever(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty())

            assertThatThrownBy {
                sharedSpaceService.createSharedSpace("공간", "unknown@test.com")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class JoinSharedSpace {

        @Test
        fun `초대 코드로 공유 공간에 참가할 수 있다`() {
            val space = SharedSpace(name = "커플 공간", inviteCode = "abc12345").apply { id = UUID.randomUUID() }
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findByInviteCode("abc12345")).thenReturn(Optional.of(space))

            val result = sharedSpaceService.joinSharedSpace("abc12345", email)

            assertThat(result.name).isEqualTo("커플 공간")
            assertThat(user.sharedSpace).isEqualTo(space)
        }

        @Test
        fun `이미 공유 공간에 속한 사용자가 참가하면 예외가 발생한다`() {
            val existingSpace = SharedSpace(name = "기존 공간").apply { id = UUID.randomUUID() }
            user.sharedSpace = existingSpace
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            assertThatThrownBy {
                sharedSpaceService.joinSharedSpace("abc12345", email)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("already belongs to a shared space")
        }

        @Test
        fun `잘못된 초대 코드로 참가하면 예외가 발생한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findByInviteCode("invalid")).thenReturn(Optional.empty())

            assertThatThrownBy {
                sharedSpaceService.joinSharedSpace("invalid", email)
            }.isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining("Invalid invite code")
        }

        @Test
        fun `공유 공간이 가득 찼을 때 참가하면 예외가 발생한다`() {
            val space = SharedSpace(name = "커플 공간", inviteCode = "abc12345").apply { id = UUID.randomUUID() }
            val user1 = User(id = 2L, name = "User1", email = "u1@test.com", role = Role.USER, provider = "google", providerId = "1", sharedSpace = space)
            val user2 = User(id = 3L, name = "User2", email = "u2@test.com", role = Role.USER, provider = "google", providerId = "2", sharedSpace = space)
            space.users.addAll(listOf(user1, user2))

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findByInviteCode("abc12345")).thenReturn(Optional.of(space))

            assertThatThrownBy {
                sharedSpaceService.joinSharedSpace("abc12345", email)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("full")
        }

        @Test
        fun `공유 공간에 1명일 때 참가할 수 있다`() {
            val space = SharedSpace(name = "커플 공간", inviteCode = "abc12345").apply { id = UUID.randomUUID() }
            val existingUser = User(id = 2L, name = "Partner", email = "partner@test.com", role = Role.USER, provider = "google", providerId = "1", sharedSpace = space)
            space.users.add(existingUser)

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))
            whenever(sharedSpaceRepository.findByInviteCode("abc12345")).thenReturn(Optional.of(space))

            val result = sharedSpaceService.joinSharedSpace("abc12345", email)

            assertThat(result.name).isEqualTo("커플 공간")
            assertThat(user.sharedSpace).isEqualTo(space)
        }
    }

    @Nested
    inner class GetMySharedSpaces {

        @Test
        fun `사용자의 공유 공간을 조회할 수 있다`() {
            val space = SharedSpace(name = "우리 공간").apply { id = UUID.randomUUID() }
            user.sharedSpace = space
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            val result = sharedSpaceService.getMySharedSpaces(email)

            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("우리 공간")
        }

        @Test
        fun `공유 공간이 없는 사용자는 빈 목록을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            val result = sharedSpaceService.getMySharedSpaces(email)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class GetMembers {

        @Test
        fun `공유 공간의 멤버를 조회할 수 있다`() {
            val space = SharedSpace(name = "커플 공간").apply { id = UUID.randomUUID() }
            user.sharedSpace = space
            val partner = User(id = 2L, name = "Partner", email = "partner@test.com", role = Role.USER, provider = "google", providerId = "2", sharedSpace = space)
            space.users.addAll(listOf(user, partner))

            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            val result = sharedSpaceService.getMembers(email)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("Test User", "Partner")
        }

        @Test
        fun `공유 공간이 없으면 빈 목록을 반환한다`() {
            whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(user))

            val result = sharedSpaceService.getMembers(email)

            assertThat(result).isEmpty()
        }
    }
}
