package com.aridclown.intellij.defold

object DefoldConstants {
    const val GAME_PROJECT_FILE = "game.project"
    const val BOB_MAIN_CLASS = "com.dynamo.bob.Bob"

    // Paging
    const val LOCALS_PAGE_SIZE = 200
    const val TABLE_PAGE_SIZE = 100

    // Safety guards for decoding Lua code (STACK dumps)
    const val STACK_STRING_TOKEN_LIMIT = 1000

    // EXEC options
    const val EXEC_MAXLEVEL = 1
}

