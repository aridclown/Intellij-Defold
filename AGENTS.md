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

## References

As a reference for the debugger, use:

- EmmyLua (`~/Projects/tmp/IntelliJ-EmmyLua/src/main/java/com/tang/intellij/lua/debugger`)
    - It provides a lot of useful MobDebug debugging functionality that we can leverage as our debugger is very similar.
- Defold (`~/Projects/tmp/defold/`) built-in debugger. It's built on Clojure, but it's also a great reference as it's
  the default debugger for in-editor Defold projects.
    - `engine/engine/content/builtins/scripts` for their slightly modified MobDebug and LuaSocket builtins.
    - `engine/content/builtins/scripts/edn.lua`: EDN for data. They added it to serialize Lua values (tables, userdata,
      etc.) into EDN strings so the Clojure editor can decode them reliably.
    - `editor/src/clj/editor/debugging/mobdebug.clj`: Same basic protocol shape as MobDebug: line-based TCP with status
      codes parsing and optional payloads.
    - `editor/debug_view.clj` and `editor/debugging/mobdebug.clj`: connection, session, controls and actions,
      breakpoints, output and eval, etc.