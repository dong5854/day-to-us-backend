package com.dong.daytous.repository

import com.dong.daytous.domain.budget.BudgetEntry
import com.dong.daytous.domain.sharedspace.SharedSpace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.util.UUID

@DataJpaTest
class BudgetEntryRepositoryTest {
    @Autowired
    lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var budgetEntryRepository: BudgetEntryRepository

    @Test
    fun `BudgetEntry를 저장하고 ID로 찾을 수 있다`() {
        // given
        val sharedSpace = entityManager.persistAndFlush(SharedSpace(name = "Test Space"))
        val budgetEntry = BudgetEntry(description = "Coffee", amount = 5.0, sharedSpace = sharedSpace)

        // when
        val savedBudgetEntry = budgetEntryRepository.saveAndFlush(budgetEntry)
        val foundOpt = budgetEntryRepository.findById(savedBudgetEntry.id!!)

        // then
        assertThat(foundOpt).isPresent
        val found = foundOpt.get()

        assertThat(found.description).isEqualTo("Coffee")
        assertThat(found.amount).isEqualTo(5.0)

        val foundSharedSpaceId = requireNotNull(found.sharedSpace).id
        assertThat(foundSharedSpaceId).isEqualTo(sharedSpace.id)
    }

    @Test
    fun `SharedSpace ID로 BudgetEntry 목록을 찾을 수 있다`() {
        // given
        val sharedSpace1 = entityManager.persistAndFlush(SharedSpace(name = "Test Space 1"))
        val sharedSpace2 = entityManager.persistAndFlush(SharedSpace(name = "Test Space 2"))

        entityManager.persist(BudgetEntry(description = "Coffee", amount = 5.0, sharedSpace = sharedSpace1))
        entityManager.persist(BudgetEntry(description = "Movie", amount = 12.0, sharedSpace = sharedSpace1))
        entityManager.persist(BudgetEntry(description = "Lunch", amount = 15.0, sharedSpace = sharedSpace2))
        entityManager.flush()
        entityManager.clear()

        // when
        val foundEntries = budgetEntryRepository.findBySharedSpaceId(sharedSpace1.id!!)

        // then
        assertThat(foundEntries).hasSize(2)
        assertThat(foundEntries.map { it.description })
            .containsExactlyInAnyOrder("Coffee", "Movie")

        // (선택) sharedSpace null 아님도 같이 체크하고 싶으면:
        assertThat(foundEntries.map { requireNotNull(it.sharedSpace).id }.distinct())
            .containsExactly(sharedSpace1.id)
    }
}
