# Changelog

User-visible changes belong under `Unreleased`. Release headings must match `releaseVersion` in `build.gradle.kts`.

## Unreleased

- Migrate existing annotation caches to bundled files and preserve working caches when updates fail.

- Fix project opening from background threads on newer IntelliJ versions.

- Load bundled API annotations from the configured Defold editor, falling back to GitHub for older editors.

## 0.2.4

- Republishes the 0.2.3 fixes with complete Marketplace release notes.
- Fixed Defold API autocomplete when annotation archives contain an extra tmp directory.
- Automatically repairs existing annotation caches stored under tmp/defold_api.
- Fixed Defold annotation setup failures caused by incompatible bundled Kotlin runtime libraries.
- Fixed EmmyLua language server startup in sandboxed IntelliJ installations.
- Restored syntax highlighting for game.project files with an IntelliJ 2025.2-compatible INI plugin.
- Updated bundled integrations and build tooling for IntelliJ IDEA 2025.2.

## 0.2.3

- Fixed Defold API autocomplete when annotation archives contain an extra tmp directory.
- Automatically repairs existing annotation caches stored under tmp/defold_api.
- Fixed Defold annotation setup failures caused by incompatible bundled Kotlin runtime libraries.
- Fixed EmmyLua language server startup in sandboxed IntelliJ installations.
- Restored syntax highlighting for game.project files with an IntelliJ 2025.2-compatible INI plugin.
- Updated bundled integrations and build tooling for IntelliJ IDEA 2025.2.
