# Category budgets deployment

Apply `scripts/add-category-budgets.sql` before deploying this backend. Production validates its schema. The migration is additive and idempotent: category order and archive columns, a category-budget rule table, and reference indexes. Existing category names and ledger records are preserved.

Budget rules are keyed by category, first day of month, and recurring flag. A monthly override takes priority, including a null amount explicitly clearing a budget. Otherwise the newest recurring rule at or before the selected month applies. Repeating a change never modifies past months or later monthly overrides. Amounts are whole won in the inclusive range 0–999,999,999,999.

Endpoints under `/shared-spaces/{spaceId}/expense-categories`:
- GET `?includeArchived=true`: ordered categories including archived history (default excludes archived for older clients).
- GET `/overview?month=YYYY-MM`: six months of expense totals and effective budgets, category usage and linked recurring source IDs.
- PATCH `/{id}` with `{name}`: rename.
- PUT `/order` with `{ids}`: all active category IDs, once each.
- PUT `/{id}/archive` with `{archived}`: archive or restore; linked recurring sources must be reassigned first.
- PUT `/{id}/budgets/{month}` with `{amount,repeat}`: monthly budget, optionally recurring. Null clears, zero is a real target.
- DELETE `/{id}`: unused categories only; history and budget rules require archive instead.

Every endpoint checks space membership. Category assignment and archival use row locks to prevent new references to a category being archived concurrently. Existing entries can keep their archived category when edited.

Rollout: successful PostgreSQL backup, additive SQL, backend image, backend startup/API checks, frontend image. The old application can run with the added schema, but its obsolete category-delete action will refuse to remove categories with history. Roll back application images if needed; retain the additive schema and budget data.

Validation: `./gradlew test`, including CategoryBudgetIntegrationTest for monthly/recurring override precedence, zero versus clearing, historical totals and archived categories, ordering, validation and cross-space isolation.
