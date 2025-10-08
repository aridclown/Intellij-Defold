import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.9.0"
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
    jvmToolchain(21)
}

dependencies {
    implementation("org.json:json:20250517")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("com.google.protobuf:protobuf-java:3.20.1")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.4")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.13.4")

    testImplementation("org.assertj:assertj-core:3.26.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
    testImplementation("io.mockk:mockk:1.14.5") {
        exclude(group = "org.jetbrains.kotlinx") // ensures only IntelliJ's is used
    }

    intellijPlatform {
        intellijIdea("2025.2") {
            type = IntelliJPlatformType.IntellijIdeaCommunity
            // use non-installer archive to avoid hdiutil on macOS
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

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
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
    patchPluginXml {
        sinceBuild.set("252")
        // keep untilBuild empty for now to avoid unnecessary pinning
    }

    test {
        useJUnitPlatform()
    }
}
