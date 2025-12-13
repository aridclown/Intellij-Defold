import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    `jvm-test-suite`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.spotless)
}

group = "com.aridclown"
version = "0.2.0"

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
    implementation(libs.ini4j)
    implementation(libs.luaj)
    implementation(libs.okhttp)

    testImplementation(libs.assertj)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    }
    testImplementation(libs.mockk) {
        exclude(group = "org.jetbrains.kotlinx") // ensures only IntelliJ's is used
    }
    testImplementation(libs.okhttp.mockwebserver)

    intellijPlatform {
        intellijIdea("2025.2")

        plugins(
            "com.cppcxy.Intellij-EmmyLua:0.18.0.94-IDEA252",
            "com.redhat.devtools.lsp4ij:0.19.0",
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

    signing {
        certificateChain = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
        password = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN")
    }
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
