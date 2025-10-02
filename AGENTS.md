# Repository Guidelines

## Project Structure & Module Organization
Source lives under <code>src/main/java/com/example/crud</code>, grouped by domain packages such as <code>controller</code>, <code>data</code>, <code>entity</code>, and <code>repository</code>; keep new modules aligned with that vertical slice layout. Thymeleaf templates and static assets sit in <code>src/main/resources/templates</code> and <code>src/main/resources/static/{css,img,js}</code>. QueryDSL outputs land in <code>build/generated/querydsl</code> and are wired into the main source set—never edit generated files manually. Integration mappers and localization bundles belong in <code>src/main/resources/{mapper,messages}</code>. Tests mirror the main tree in <code>src/test/java/com/example/crud</code> with per-feature subpackages.

## Build, Test, and Development Commands
Use <code>./gradlew clean build</code> for a full verify, including generated sources. <code>./gradlew bootRun</code> starts the Spring Boot app with the default profile; add <code>--args='--spring.profiles.active=local'</code> to switch configs. Run <code>./gradlew test</code> for the JUnit 5 suite. When exercising Postgres or Redis integrations locally, bring them up via <code>docker-compose up -d db redis</code>, then run the application or tests that rely on them.

## Coding Style & Naming Conventions
Stick to Java 17, four-space indentation, and Lombok constructors (e.g., <code>@RequiredArgsConstructor</code>) instead of field injection. Controllers and services use PascalCase names, methods and variables use camelCase, and configuration constants stay in SCREAMING_SNAKE_CASE. Keep loggers declared as <code>private static final Logger log = LoggerFactory.getLogger(...)</code>. Prefer expressive method names such as <code>getAddProduct</code>. DTOs live under <code>data/*/dto</code>; keep them immutable where practical. MapStruct mappers must use the Spring component model as configured via compiler args.

## Testing Guidelines
Write Spring Boot and slice tests in <code>src/test/java</code>, following the <code>WhateverTest</code> naming pattern. The suite already leverages Testcontainers for Postgres; leave Docker running when executing database-dependent tests. Use Mockito or WebTestClient consistent with existing patterns, and cover new repository or service logic with both happy-path and failure cases. Keep tests isolated by resetting Redis or external state through container fixtures rather than manual scripts.

## Commit & Pull Request Guidelines
Follow the Conventional Commit prefixes seen in history (for example <code>feat:</code>, <code>chore:</code>, <code>docs:</code>). Keep messages imperative and under 72 characters after the prefix. Pull requests should describe scope, testing performed (<code>./gradlew test</code>, manual UI, etc.), and link to any related issue or document. Include screenshots or cURL snippets when altering UI or API responses, and confirm docker-compose services still start when infrastructure changes are introduced.

## Configuration & Security Notes
Sensitive overrides stay outside the repo (e.g., <code>application-secrets.properties</code>). When sharing environment variables, reference keys only. Validate new external integrations through the actuator health endpoint exposed at <code>/actuator/health</code> before tagging releases.
