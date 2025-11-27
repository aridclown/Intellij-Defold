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