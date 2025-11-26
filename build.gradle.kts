import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    `jvm-test-suite`
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "com.aridclown.Intellij-Defold"
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
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.4")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.13.4")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    }
    testImplementation("io.mockk:mockk:1.14.5") {
        exclude(group = "org.jetbrains.kotlinx") // ensures only IntelliJ's is used
    }
    testImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")

    intellijPlatform {
        intellijIdea("2025.2") {
            type = IntelliJPlatformType.IntellijIdeaCommunity
            // use non-installer archive to avoid hdiutil on macOS
            useInstaller = false
        }

        plugins(
            "com.cppcxy.Intellij-EmmyLua:0.17.0.89-IDEA252",
            "com.redhat.devtools.lsp4ij:0.15.0",
            "OpenGL-Plugin:1.1.6",
            "com.jetbrains.plugins.ini4idea:252.23892.449",
        )

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    projectName = "IntelliJ-Defold"
}

spotless {
    kotlin {
        ktlint("1.8.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ij_kotlin_allow_trailing_comma" to "false",
                "ij_kotlin_allow_trailing_comma_on_call_site" to "false",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        ktlint("1.8.0").editorConfigOverride(
            mapOf("ij_kotlin_allow_trailing_comma" to "false"),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("252")
        // keep untilBuild empty for now to avoid unnecessary pinning
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
            }

            sources {
                compileClasspath += sourceSets.test.get().compileClasspath
                runtimeClasspath += sourceSets.test.get().runtimeClasspath
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        dependsOn(tasks.named("prepareTestSandbox"))

                        forkEvery = 1

                        val mainTestTask = tasks.named<Test>("test").get()
                        classpath += mainTestTask.classpath
                        jvmArgumentProviders.add { mainTestTask.allJvmArgs }

                        doFirst {
                            systemProperties.putAll(mainTestTask.systemProperties)
                        }
                    }
                }
            }
        }

        tasks.check { dependsOn(integrationTest) }
    }
}
