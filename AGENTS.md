# Repository Guidelines

## Testing Guidelines

- No test suite yet. Prefer JUnit 5 for unit tests under `src/test/kotlin/**`.
- For IDE integration, consider IntelliJ Platform functional tests when adding complex behaviors.
- Run tests with `./gradlew test` and aim for meaningful coverage on new code.

## Security & Configuration Tips

- IntelliJ Platform + plugins are declared in `build.gradle.kts` under `intellijPlatform { ... }`.
- Use `intellijIdeaUltimate("2025.2") { useInstaller = false }` as configured; avoid ad‑hoc IDE versions in PRs.
- Adding dependencies: prefer `implementation(...)` and declare new platform plugins in `intellijPlatform.plugins(...)`
  only when required.

## Architecture Overview

- Plugin provides Defold integration with MobDebug debugging support, tool window scaffolding, and project services.
- See `debugger/*` for runtime/debug logic and `DefoldProjectActivity` for startup wiring.

## Extra

- For the debugger, use `~/Projects/tmp/IntelliJ-EmmyLua/src/main/java/com/tang/intellij/lua/debugger` as a reference.
  It provides a lot of useful debugging functionality that we can leverage as our debugger is very similar.