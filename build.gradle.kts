plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.defold.ij.plugin"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    // 2025.2 platform requires Java 17 toolchain
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        // Target IntelliJ IDEA Community 2025.2 (use non-installer archive to avoid hdiutil on macOS)
        intellijIdeaCommunity("2025.2") {
            useInstaller = false
        }
        // EmmyLua2 plugin from Marketplace
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
        plugins("com.cppcxy.Intellij-EmmyLua:0.12.0.73-IDEA252", "com.redhat.devtools.lsp4ij:0.15.0")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    projectName = "IntelliJ-Defold"

    pluginConfiguration {
        name = "Defold"
    }
}

tasks {
    wrapper {
        gradleVersion = "8.8"
        distributionType = Wrapper.DistributionType.ALL
    }

    patchPluginXml {
        sinceBuild.set("252")
        // keep untilBuild empty for now to avoid unnecessary pinning
    }

    runIde {
        // Use the same Java toolchain for the sandbox
        jvmArgs = listOf()
    }
}
