package com.aridclown.intellij.defold

object DefoldConstants {
    const val GAME_PROJECT_FILE = "game.project"
    const val BOB_MAIN_CLASS = "com.dynamo.bob.Bob"
    const val CONFIG_FILE_NAME = "config"
    const val MACOS_RESOURCES_PATH = "Contents/Resources"

    // ---- Game Project INI -------------------------------------------------------

    // INI section names
    const val INI_BUILD_SECTION = "build"
    const val INI_BOOTSTRAP_SECTION = "bootstrap"
    const val INI_LAUNCHER_SECTION = "launcher"

    // INI property keys
    const val INI_VERSION_KEY = "version"
    const val INI_EDITOR_SHA1_KEY = "editor_sha1"
    const val INI_RESOURCESPATH_KEY = "resourcespath"
    const val INI_JDK_KEY = "jdk"
    const val INI_JAVA_KEY = "java"
    const val INI_JAR_KEY = "jar"
    const val INI_DEBUG_INIT_SCRIPT_KEY = "debug_init_script"

    // INI property values
    const val INI_DEBUG_INIT_SCRIPT_VALUE = "/debugger/mobdebug_init.luac"

    // ---- Debugger -------------------------------------------------------

    const val DEFAULT_MOBDEBUG_PORT = 8172

    // Vars
    const val GLOBAL_DISPLAY_NAME = "_G"
    const val VARARG_DISPLAY_NAME = "..."

    // Paging
    const val LOCALS_PAGE_SIZE = 200
    const val TABLE_PAGE_SIZE = 100

    // Safeguards
    const val STACK_STRING_TOKEN_LIMIT = 10000
    const val VARARG_PREVIEW_LIMIT = 3

    // maxlevel options
    const val EXEC_MAXLEVEL = 1
    const val STACK_MAXLEVEL = 0
}
