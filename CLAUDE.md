# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build                # Full build (compile + test)
./gradlew test                 # Run all tests
./gradlew test --tests "com.dong.daytous.service.ScheduleServiceTest"  # Run single test class
./gradlew test --tests "*.ScheduleServiceTest.testMethodName"          # Run single test method
./gradlew clean build          # Clean rebuild
./gradlew compileKotlin        # Compile only (no tests)
./gradlew bootRun              # Run the application (requires dev profile config)
```

Tests use H2 in-memory database via `application-test.properties` — no external DB needed.

## Architecture

Kotlin + Spring Boot 4.0.1 layered architecture for a couples' shared app (DayToUs).

**Layer flow:** Controller → Service (@Transactional) → Repository (Spring Data JPA) → Entity

**DTO pattern:** Request/Response DTOs live in `dto/` with companion Mapper objects (e.g., `ScheduleMapper`) that handle entity↔DTO conversion. Mappers are standalone objects, not injected beans.

**Entity ID strategy:** SharedSpace, Schedule, BudgetEntry use UUID v7 (Hibernate UuidGenerator). User uses Long auto-increment.

**Authentication flow:**
1. Google OAuth2 login → `CustomOAuth2UserService` loads/creates user → `OAuth2AuthenticationSuccessHandler` issues JWT
2. Subsequent requests: `JwtAuthenticationFilter` extracts JWT → `JwtTokenProvider` validates → sets SecurityContext
3. Controllers get authenticated user email from SecurityContext principal

**Google Calendar bidirectional sync:**
- `GoogleCalendarService`: Direct Google Calendar API calls (push/pull/update/delete events)
- `GoogleCalendarSyncService`: Orchestrates sync with conflict detection, runs on @Scheduled interval (10min default)
- OAuth2 tokens (access/refresh) stored encrypted via `TokenEncryptor` (AES-GCM) in `GoogleToken` entity
- Sync status tracked per Schedule: LOCAL_ONLY → PENDING → SYNCED (or CONFLICT)
- SyncDirection per user: BIDIRECTIONAL, APP_TO_GOOGLE, GOOGLE_TO_APP

**Profiles:** `dev` (local PostgreSQL, ddl-auto=update), `prod` (env vars, ddl-auto=validate), `test` (H2, auto)

**Sensitive config files (gitignored):** `application-oauth.properties`, `application-jwt.properties`

## Git Commit Rules

- Commit messages must be in English, following existing history style: `type(scope): Description`
- Never include `Co-Authored-By` line
- Do not commit `CLAUDE.md`

## Conventions

- Kotlin with `-Xjsr305=strict` for strict null safety
- kotlin-jpa plugin provides no-arg constructors for entities; kotlin-spring opens Spring-annotated classes
- Global exception handling via `GlobalExceptionHandler` (@RestControllerAdvice)
- API docs at `/swagger-ui/index.html` with JWT bearer auth configured
