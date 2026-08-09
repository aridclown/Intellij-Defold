# Project Guidelines

- Simplicity is key. Favor clarity and maintainability over cleverness.
- Less is more. Follow Kotlin coding conventions. If it can be written in a more idiomatic way in Kotlin, do so. Leverage the language capabilities to ensure the cleanest possible code.
- Use basic language when explaining anything; avoid overcomplicating.

## Coding Guidelines

- Favor `java.nio` and IntelliJ file manipulation APIs over `java.io` for file I/O.


## Testing Guidelines

- For IDE integration, consider IntelliJ Platform functional tests when adding complex behaviors.
- Test names should follow self-documenting Kotlin backtick conventions and be descriptive.
- Test names should describe behavior and features, not implementation details (avoid method/class names in test names).
- Use assertj for assertions.
  - When asserting, prefer `extracting()` over multiple `assertThat()` calls.
- Share reusable helpers across tests to avoid boilerplate.

## Releases and Deployment

- The plugin version is set explicitly in `build.gradle.kts`. CI does not generate or bump it.
- Every push runs `.github/workflows/ci.yml`, which executes `./gradlew check --no-daemon`.
- `.github/workflows/build-plugin.yml` is manual. Trigger it with `gh workflow run build-plugin.yml --ref main` after the version commit is on `main`.
- The build workflow uploads a `plugin-distributions` Actions artifact containing `IntelliJ-Defold-<version>.zip`.
- The build workflow does not create a Git tag or GitHub Release, and it does not publish to JetBrains Marketplace. Do not describe its artifact as a published release.
