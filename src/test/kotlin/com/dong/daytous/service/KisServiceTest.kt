package com.dong.daytous.service

import com.dong.daytous.config.encrypt.TokenEncryptor
import com.dong.daytous.domain.savings.*
import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.*
import com.dong.daytous.dto.KisConnectRequest
import com.dong.daytous.dto.SavingsPlanRequest
import com.dong.daytous.repository.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["savings.auto-post.enabled=false"])
@ActiveProfiles("test")
@Transactional
class KisServiceTest {
    @Autowired lateinit var connections: KisConnectionRepository
    @Autowired lateinit var plans: SavingsPlanRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var spaces: SharedSpaceRepository
    @Autowired lateinit var encryptor: TokenEncryptor
    @Autowired lateinit var savings: SavingsService
    @Autowired lateinit var records: SavingsRecordRepository
    private val client = mock<KisClient>()
    private val owner = "${UUID.randomUUID()}@test.com"
    private fun service() = KisService(connections, plans, users, encryptor, client, owner)
    private fun plan(): SavingsPlan {
        val space = spaces.save(SharedSpace("연결 테스트"))
        users.save(User(name = "본인", email = owner, role = Role.USER, provider = "google", providerId = owner, sharedSpace = space))
        whenever(client.token(any(), any())).thenReturn(KisToken("sensitive-token", 86400))
        whenever(client.balance(any(), any(), any(), any(), any())).thenReturn(KisBalance(8500000, 200000))
        return plans.saveAndFlush(SavingsPlan(sharedSpace = space, name = "IRP", payerEmail = owner, payerName = "본인", kind = SavingsKind.IRP))
    }
    private fun request(consent: Boolean = true) = KisConnectRequest("12345678", "sensitive-key", "sensitive-secret", consent)

    @Test fun `키를 암호화하고 잔고는 납입 기록에 합산하지 않는다`() {
        val p = plan(); val service = service()
        service.connect(p.sharedSpace.id!!, p.id!!, request(), owner)
        val c = connections.findById(p.id!!).orElseThrow()
        assertThat(c.appKeyEncrypted).doesNotContain("sensitive-key")
        assertThat(encryptor.decrypt(c.appSecretEncrypted)).isEqualTo("sensitive-secret")
        assertThat(records.findByPlanId(p.id!!)).isEmpty()
        val result = service.status(p.sharedSpace.id!!, owner)
        assertThat(result.accounts.single().valuation).isEqualTo(8500000)
        assertThat(result.accounts.single().maskedAccount).isEqualTo("****5678-29")
        assertThat(result.toString()).doesNotContain("sensitive", "12345678")
        verify(client).balance("sensitive-key", "sensitive-secret", "sensitive-token", "12345678", "29")
    }
    @Test fun `동의와 명의자 권한을 검증하고 공간 밖에는 공개하지 않는다`() {
        val p = plan(); val service = service()
        assertThatThrownBy { service.connect(p.sharedSpace.id!!, p.id!!, request(false), owner) }.isInstanceOf(IllegalArgumentException::class.java)
        val partner = "$owner.partner"
        users.save(User(name = "멤버", email = partner, role = Role.USER, provider = "google", providerId = partner, sharedSpace = p.sharedSpace))
        assertThatThrownBy { service.connect(p.sharedSpace.id!!, p.id!!, request(), partner) }.isInstanceOf(IllegalArgumentException::class.java)
        service.connect(p.sharedSpace.id!!, p.id!!, request(), owner)
        assertThat(service.status(p.sharedSpace.id!!, partner).accounts.single().canManage).isFalse()
        assertThatThrownBy { service.disconnect(p.sharedSpace.id!!, p.id!!, partner) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.status(UUID.randomUUID(), owner) }.isInstanceOf(IllegalArgumentException::class.java)
    }
    @Test fun `중복 연결을 거부하고 토큰을 재사용하며 실패해도 마지막 잔고를 유지한다`() {
        val p = plan(); val service = service()
        service.connect(p.sharedSpace.id!!, p.id!!, request(), owner)
        assertThatThrownBy { service.connect(p.sharedSpace.id!!, p.id!!, request(), owner) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.sync(p.sharedSpace.id!!, p.id!!, owner) }.isInstanceOf(IllegalArgumentException::class.java)
        val c = connections.findById(p.id!!).orElseThrow()
        c.lastAttemptAt = Instant.now().minusSeconds(120)
        whenever(client.balance(any(), any(), any(), any(), any())).thenThrow(RuntimeException("sensitive-secret"))
        service.sync(p.sharedSpace.id!!, p.id!!, owner)
        assertThat(c.valuation).isEqualTo(8500000)
        assertThat(c.error).isNotNull().doesNotContain("sensitive-secret")
        verify(client, times(1)).token(any(), any())
        service.disconnect(p.sharedSpace.id!!, p.id!!, owner)
        assertThat(connections.existsById(p.id!!)).isFalse()
    }
    @Test fun `일일 조회는 중복 실행하지 않고 탈퇴한 명의자는 조회하지 않는다`() {
        val p = plan(); val service = service()
        service.connect(p.sharedSpace.id!!, p.id!!, request(), owner)
        service.syncDue(p.id!!)
        verify(client, times(1)).balance(any(), any(), any(), any(), any())
        val c = connections.findById(p.id!!).orElseThrow()
        c.lastAttemptAt = Instant.now().minusSeconds(86401)
        service.syncDue(p.id!!)
        verify(client, times(2)).balance(any(), any(), any(), any(), any())
        c.lastAttemptAt = Instant.now().minusSeconds(86401)
        users.findByEmail(owner).orElseThrow().sharedSpace = null
        service.syncDue(p.id!!)
        verify(client, times(2)).balance(any(), any(), any(), any(), any())
    }
    @Test fun `연결 중 종류 변경을 막고 종료할 때 인증정보를 제거한다`() {
        val p = plan(); val service = service()
        service.connect(p.sharedSpace.id!!, p.id!!, request(), owner)
        assertThatThrownBy {
            savings.preview(p.sharedSpace.id!!, p.id!!, SavingsPlanRequest(name = p.name, payerEmail = owner, kind = SavingsKind.GENERAL), owner)
        }.isInstanceOf(IllegalArgumentException::class.java)
        savings.archive(p.sharedSpace.id!!, p.id!!, owner)
        assertThat(connections.existsById(p.id!!)).isFalse()
        assertThat(p.archived).isTrue()
    }
}
