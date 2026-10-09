package com.dong.daytous.service

import com.dong.daytous.domain.sharedspace.SharedSpace
import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.dto.BudgetEntryRequest
import com.dong.daytous.repository.BudgetEntryRepository
import com.dong.daytous.repository.SharedSpaceRepository
import com.dong.daytous.repository.UserRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BudgetUpdateIntegrationTest {
    @Autowired lateinit var budgetService: BudgetService
    @Autowired lateinit var budgetEntryRepository: BudgetEntryRepository
    @Autowired lateinit var sharedSpaceRepository: SharedSpaceRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var entityManager: EntityManager

    @Test
    fun `가계부를 반복 수정해도 기존 ID와 행 수를 유지한다`() {
        val space = sharedSpaceRepository.save(SharedSpace(name = "수정 테스트"))
        val email = "budget-update@test.com"
        userRepository.save(User(
            name = "Test User", email = email, role = Role.USER,
            provider = "google", providerId = "budget-update", sharedSpace = space,
        ))
        val spaceId = space.id!!
        val original = budgetService.createBudgetEntry(
            spaceId, BudgetEntryRequest("커피", -5000.0, LocalDate.of(2026, 10, 7)), email,
        )
        val entryId = original.id!!
        entityManager.flush()
        entityManager.clear()

        repeat(2) { index ->
            val request = BudgetEntryRequest("라떼", -6000.0 - index, LocalDate.of(2026, 10, 8))
            val updated = budgetService.updateBudgetEntry(spaceId, entryId, request, email)
            assertThat(updated.id).isEqualTo(entryId)
            entityManager.flush()
            entityManager.clear()

            val entries = budgetEntryRepository.findBySharedSpaceId(spaceId)
            assertThat(entries).hasSize(1)
            assertThat(entries.single().id).isEqualTo(entryId)
            assertThat(entries.single().description).isEqualTo(request.description)
            assertThat(entries.single().amount).isEqualTo(request.amount)
            assertThat(entries.single().date).isEqualTo(request.date)
        }
    }
}
