package com.aridclown.intellij.defold.debugger

import kotlinx.html.emptyMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class MobDebugPathMapperTest {
    @Test
    fun `returns null when mappings are empty`() {
        val mapper = MobDebugPathMapper(emptyMap)

        assertThat(mapper.toRemote("/local/path/file.lua")).isNull()
        assertThat(mapper.toLocal("remote/path/file.lua")).isNull()
    }

    @Test
    fun `ignores mappings with blank endpoints`() {
        val mapper =
            MobDebugPathMapper(
                mapOf(
                    "" to "remote",
                    "local" to "",
                    "  " to "remote2",
                    "local2" to "  "
                )
            )

        assertThat(mapper.toRemote("/local/file.lua")).isNull()
        assertThat(mapper.toRemote("/local2/file.lua")).isNull()
    }

    @ParameterizedTest
    @MethodSource("toRemoteScenarios")
    fun `maps local path to configured remote prefix`(
        mappings: Map<String, String>,
        localPath: String,
        expectedRemote: String?
    ) {
        assertThat(
            MobDebugPathMapper(mappings).toRemote(localPath)
        ).isEqualTo(expectedRemote)
    }

    @ParameterizedTest
    @MethodSource("toLocalScenarios")
    fun `maps remote path to configured local prefix`(
        mappings: Map<String, String>,
        remotePath: String,
        expectedLocal: String?
    ) {
        val mapper = MobDebugPathMapper(mappings)
        assertThat(mapper.toLocal(remotePath)).isEqualTo(expectedLocal)
    }

    @Test
    fun `normalizes trailing slashes before mapping`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project/" to "game/")
            )

        assertThat(mapper.toRemote("/project/main.lua")).isEqualTo("game/main.lua")
    }

    @Test
    fun `strips local prefix when remote root is empty`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project" to "")
            )

        assertThat(mapper.toRemote("/project/main.lua")).isEqualTo("main.lua")
    }

    @Test
    fun `returns null for inputs outside configured roots`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project/game" to "game")
            )

        assertThat(mapper.toRemote("/other/main.lua")).isNull()
        assertThat(mapper.toLocal("other/main.lua")).isNull()
    }

    @Test
    fun `prefers most specific mapping when multiple roots apply`() {
        val mapper =
            MobDebugPathMapper(
                mapOf(
                    "/project/game" to "game",
                    "/project" to "root"
                )
            )

        // Should use first match (/project/game)
        assertThat(mapper.toRemote("/project/game/main.lua")).isEqualTo("game/main.lua")
    }

    @Test
    fun `accepts system dependent separators`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project" to "game")
            )

        // Should normalize Windows-style separators
        assertThat(mapper.toLocal("game/scripts/main.lua")).isNotNull()
    }

    @Test
    fun `simplifies relative fragments before matching`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project/game/../build" to "build")
            )

        assertThat(mapper.toRemote("/project/build/main.lua")).isEqualTo("build/main.lua")
    }

    @Test
    fun `preserves nested directory structure`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project/game" to "game")
            )

        assertThat(mapper.toRemote("/project/game/scripts/player/init.lua"))
            .isEqualTo("game/scripts/player/init.lua")
        assertThat(mapper.toLocal("game/scripts/player/init.lua"))
            .isEqualTo("/project/game/scripts/player/init.lua")
    }

    @Test
    fun `does not map back when remote root is empty`() {
        val mapper =
            MobDebugPathMapper(
                mapOf("/project" to "")
            )

        // Can't convert back without a remote prefix
        assertThat(mapper.toLocal("main.lua")).isNull()
    }

    companion object {
        @JvmStatic
        fun toRemoteScenarios() = listOf(
            arguments(mapOf("/project/game" to "game"), "/project/game/main.lua", "game/main.lua"),
            arguments(mapOf("/project/game" to "game"), "/project/game/scripts/init.lua", "game/scripts/init.lua"),
            arguments(
                mapOf("/home/user/defold" to "defold/game"),
                "/home/user/defold/player.lua",
                "defold/game/player.lua"
            ),
            arguments(mapOf("/project" to "game"), "/other/main.lua", null)
        )

        @JvmStatic
        fun toLocalScenarios() = listOf(
            arguments(mapOf("/project/game" to "game"), "game/main.lua", "/project/game/main.lua"),
            arguments(mapOf("/project/game" to "game"), "game/scripts/init.lua", "/project/game/scripts/init.lua"),
            arguments(
                mapOf("/home/user/defold" to "defold/game"),
                "defold/game/player.lua",
                "/home/user/defold/player.lua"
            ),
            arguments(mapOf("/project" to "game"), "other/main.lua", null)
        )
    }
}
