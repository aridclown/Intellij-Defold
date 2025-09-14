plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.aridclown.intellij.defold"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
    // EmmyLua/Sumneko Lua plugin classes are compiled with a Kotlin pre-release.
    // Suppress the compiler's pre-release check so we can reference their PSI types.
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

dependencies {
    implementation("org.json:json:20250517")
    implementation("org.ini4j:ini4j:0.5.4")
    // LuaJ for evaluating MobDebug STACK/EXEC dumps client-side (EmmyLua strategy)
    implementation("org.luaj:luaj-jse:3.0.1")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.11")
    // Satisfy IntelliJ test framework's optional JUnit4 classes (TestRule) on classpath
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        // use non-installer archive to avoid hdiutil on macOS
        intellijIdeaUltimate("2025.2") {
            useInstaller = false
        }
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
        plugins(
            "com.tang:1.4.22-IDEA2025.2",
//            "com.cppcxy.Intellij-SumnekoLua:3.15.0.46-IDEA243",
            "com.redhat.devtools.lsp4ij:0.15.0",
            "OpenGL-Plugin:1.1.6",
            "com.jetbrains.plugins.ini4idea:252.23892.449"
        )
    }
}

intellijPlatform {
    buildSearchableOptions = false
    projectName = "IntelliJ-Defold"

    pluginConfiguration {
        name = "IntelliJ-Defold"
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

    test {
        useJUnitPlatform()
    }
}
