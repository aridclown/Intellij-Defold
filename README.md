# IntelliJ Defold Debugger (MobDebug)

This plugin adds experimental attach-only debugging support for Defold projects
using the [MobDebug](https://github.com/pkulchenko/MobDebug) protocol. The
implementation is intentionally minimal and focuses on remote debugging of Lua
scripts running in a Defold game.

## Features
- Attach to a running Defold game (`mobdebug.start(host, port)`).
- Pause and resume execution, step over/into/out.
- Toggle breakpoints in Lua files and hit them from the game.
- View stack frames, locals and upvalues (basic string presentation).
- Map remote and local paths for correct breakpoint resolution.
- A dedicated console tab shows raw protocol traffic for troubleshooting.

## Testing the Debugger
1. Build the plugin:
   ```bash
   ./gradlew build
   ```
2. Install the generated plugin from `build/distributions` into IntelliJ.
3. In your Defold project, start the game with MobDebug:
   ```lua
   local mobdebug = require('mobdebug.mobdebug')
   mobdebug.start('127.0.0.1', 8172)
   mobdebug.pause() -- optional: break on start
   ```
4. In IntelliJ, create a **Defold MobDebug** run configuration and set:
   - Host and port matching the `mobdebug.start` call.
   - Local and remote root paths for path mapping.
5. Place breakpoints in your Lua source and run the configuration with the
   *Debug* executor to attach.
6. Execution should suspend either at `mobdebug.pause()` or when a breakpoint is
   hit. Step through code and inspect variables.
7. Stop the session to disconnect; the game will continue running.

## Notes
This debugger is an MVP and does not support launch configurations, coroutines,
advanced breakpoint conditions or hot reloading.
