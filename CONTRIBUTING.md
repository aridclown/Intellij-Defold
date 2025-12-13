# 🙌 Contributing Guide

Hey! Thanks for caring about this plugin. Whether you found a typo or want to ship a big feature, the notes below should make it easy to jump in.

## 🐞 Reporting Issues

1. Browse the [Issues](https://github.com/aridclown/Intellij-Defold/issues) to ensure the issue hasn't been reported.
2. If not, open a new issue and provide as much detail as possible, such as a screenshot or logs, steps to reproduce, environment details (OS, IDE version, etc.).

## ⚙️ Prerequisites

- IntelliJ IDEA 2025.2 or newer.
- JDK 21+. The Gradle wrapper handles the rest.
- Git and a GitHub account if you're planning a pull request.

## 🧰 Installing

1. Fork the repo and clone it locally.
2. Open the project in IntelliJ IDEA.
3. Gradle adds a `runIde` task; use it to spin up the sandbox IDE whenever you want to test changes.

## 🧭 Development guidelines

- Write Kotlin that's easy to read. If you have to choose, pick clear over clever.
- Reach for `java.nio.file` APIs (`Path`, `Files`, etc.) instead of `java.io` whenever dealing with files.
- Sprinkle comments only when they genuinely explain intent.
- Keep the package layout tidy.
- Lean on IntelliJ Platform APIs before inventing custom solutions.

## 🧪 Running tests

- Unit tests live in `src/test`. Integration tests live in `src/integrationTest`.
- Test names use Kotlin backticks and should describe the behavior, e.g. ``fun `downloads api when cache missing`()``.
- Use AssertJ for assertions and reuse existing helpers instead of crafting new fixtures from scratch.
- Run what you touched:
  - `./gradlew test` for the fast suite.
  - `./gradlew integrationTest` when your change affects IDE flows or Defold integration.

## 🚀 Submitting changes

1. Branch from `main` and keep commits scoped.
2. Make sure tests pass locally.
3. Run `./gradlew spotlessApply` to format the codebase before pushing.
4. Push and open a PR. Make sure the description is informative and links to relevant issues.
5. Stay close to the conversation. Reviews are collaborative, not a final exam.

## 📚 Development Resources
- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/)
- [Kotlin Programming Guide](https://kotlinlang.org/docs/)
- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
