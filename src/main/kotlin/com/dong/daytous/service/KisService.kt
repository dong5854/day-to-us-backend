package com.dong.daytous.service

import com.dong.daytous.config.encrypt.TokenEncryptor
import com.dong.daytous.domain.kis.KisConnection
import com.dong.daytous.domain.savings.SavingsKind
import com.dong.daytous.dto.*
import com.dong.daytous.repository.*
import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class KisService(private val connections: KisConnectionRepository, private val plans: SavingsPlanRepository,
    private val users: UserRepository, private val encryptor: TokenEncryptor, private val client: KisClient,
    @Value("\${kis.allowed-emails:}") private val allowedEmails: String) {
    private fun allowed(email: String) = email in allowedEmails.split(',').map(String::trim).filter(String::isNotEmpty)
    private fun member(spaceId: UUID, email: String) {
        require(users.findByEmail(email).orElseThrow().sharedSpace?.id == spaceId) { "공유 공간의 멤버만 사용할 수 있습니다" }
    }
    private fun ownedPlan(spaceId: UUID, id: UUID, email: String) = (plans.lockById(id)
        ?: throw EntityNotFoundException("연금 계좌를 찾을 수 없습니다")).also {
        member(spaceId, email)
        require(it.sharedSpace.id == spaceId && it.payerEmail == email) { "명의자 본인만 연결을 관리할 수 있습니다" }
    }
    fun status(spaceId: UUID, email: String): KisStatusResponse {
        member(spaceId, email)
        val accounts = connections.findByPlanSharedSpaceId(spaceId).filter { !it.plan.archived && it.plan.payerEmail == it.ownerEmail }
        return KisStatusResponse(allowed(email), accounts.map { c -> KisAccountResponse(c.planId, c.plan.name,
            "****${c.accountLastFour}-${c.productCode}", c.valuation, c.cash, c.lastSyncedAt, c.error, c.ownerEmail == email) },
            if (allowed(email)) plans.findBySharedSpaceId(spaceId).filter { !it.archived && it.payerEmail == email &&
                it.kind in listOf(SavingsKind.PENSION_SAVINGS, SavingsKind.IRP) && accounts.none { c -> c.planId == it.id } }.map { it.id!! }
            else emptyList())
    }
    fun connect(spaceId: UUID, id: UUID, r: KisConnectRequest, email: String) {
        users.findByEmailForUpdate(email).orElseThrow()
        val plan = ownedPlan(spaceId, id, email)
        require(allowed(email)) { "계좌 연결은 현재 테스트 사용자에게 제공됩니다" }
        require(!plan.archived && plan.kind in listOf(SavingsKind.PENSION_SAVINGS, SavingsKind.IRP)) { "연금저축 또는 IRP 계좌를 선택해 주세요" }
        require(r.shareConsent) { "공간 멤버와 잔고 공유에 동의해 주세요" }
        require(r.accountNumber.matches(Regex("[0-9]{8}"))) { "계좌번호 앞 8자리를 입력해 주세요" }
        require(r.appKey.length in 10..256 && r.appSecret.length in 10..512 &&
            r.appKey.none { it.isWhitespace() || it.isISOControl() } && r.appSecret.none { it.isWhitespace() || it.isISOControl() }) { "API 키를 확인해 주세요" }
        require(!connections.existsById(id)) { "이미 연결된 계좌입니다. 연결 해제 후 다시 시도해 주세요" }
        val product = if (plan.kind == SavingsKind.IRP) "29" else "22"
        val existing = connections.findByOwnerEmail(email)
        require(existing.size < 5) { "최대 5개 계좌까지 연결할 수 있습니다" }
        require(existing.none { encryptor.decrypt(it.accountEncrypted) == r.accountNumber && it.productCode == product }) { "이미 연결한 한국투자증권 계좌입니다" }
        val c = KisConnection(id, plan, email, product, encryptor.encrypt(r.accountNumber), encryptor.encrypt(r.appKey),
            encryptor.encrypt(r.appSecret), r.accountNumber.takeLast(4))
        refresh(c)
        connections.save(c)
    }
    fun sync(spaceId: UUID, id: UUID, email: String) {
        users.findByEmailForUpdate(email).orElseThrow()
        ownedPlan(spaceId, id, email)
        require(allowed(email)) { "자동 조회가 비활성화되어 있습니다" }
        val c = connections.findById(id).orElseThrow()
        require(c.ownerEmail == email) { "명의자 본인만 조회할 수 있습니다" }
        require(c.lastAttemptAt == null || c.lastAttemptAt!!.isBefore(Instant.now().minusSeconds(60))) { "조회 후 1분 뒤 다시 시도해 주세요" }
        refresh(c)
    }
    fun disconnect(spaceId: UUID, id: UUID, email: String) {
        ownedPlan(spaceId, id, email)
        connections.findById(id).ifPresent { c ->
            require(c.ownerEmail == email) { "명의자 본인만 연결을 해제할 수 있습니다" }
            connections.delete(c)
        }
    }
    fun syncDue(id: UUID) {
        val owner = connections.findById(id).orElse(null)?.ownerEmail ?: return
        users.findByEmailForUpdate(owner).orElse(null) ?: return
        val plan = plans.lockById(id) ?: return
        val c = connections.findById(id).orElse(null) ?: return
        if (!allowed(c.ownerEmail) || plan.archived || plan.payerEmail != c.ownerEmail ||
            users.findByEmail(c.ownerEmail).orElse(null)?.sharedSpace?.id != plan.sharedSpace.id) return
        if (c.lastAttemptAt?.isAfter(Instant.now().minusSeconds(86400)) == true) return
        refresh(c)
    }
    private fun refresh(c: KisConnection) {
        c.lastAttemptAt = Instant.now()
        try {
            val key = encryptor.decrypt(c.appKeyEncrypted)
            val secret = encryptor.decrypt(c.appSecretEncrypted)
            if (c.tokenEncrypted == null || c.tokenExpiresAt?.isAfter(Instant.now().plusSeconds(60)) != true) {
                val cached = connections.findByOwnerEmail(c.ownerEmail).firstOrNull {
                    it.tokenEncrypted != null && it.tokenExpiresAt?.isAfter(Instant.now().plusSeconds(60)) == true &&
                        encryptor.decrypt(it.appKeyEncrypted) == key
                }
                if (cached != null) {
                    c.tokenEncrypted = cached.tokenEncrypted; c.tokenExpiresAt = cached.tokenExpiresAt
                } else {
                    val token = client.token(key, secret)
                    c.tokenEncrypted = encryptor.encrypt(token.value)
                    c.tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn)
                }
            }
            val balance = client.balance(key, secret, encryptor.decrypt(c.tokenEncrypted!!), encryptor.decrypt(c.accountEncrypted), c.productCode)
            c.valuation = balance.valuation; c.cash = balance.cash; c.lastSyncedAt = Instant.now(); c.error = null
        } catch (_: Exception) {
            // Never persist or return broker responses: they may contain credentials or account data.
            c.error = "조회에 실패했습니다. 마지막 정상 조회 금액을 표시합니다. 연결 정보와 API 이용 상태를 확인해 주세요."
        }
    }
}
