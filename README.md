# BooYahGBA

A Nintendo GameBoy Advance emulator/debugger written in Java — a modernized fork of [YahGBA](https://github.com/khaledev/YahGBA) by [@khaledev](https://github.com/khaledev).

YahGBA was originally written as a learning project in 2006. This fork picks up where it left off: migrating to a Gradle build system, fixing CPU and graphics bugs, improving memory and DMA accuracy, and adding debugging/diagnostic tooling.

**Status:** Work in progress. Many commercial ROMs boot and show in-game graphics, but accuracy is incomplete. Sound is not yet emulated.

## Requirements
- Java 8+ (JDK for building, JRE for running)
- A GBA BIOS file (`gba_bios.bin`)
- A GBA ROM file (`.gba`, `.agb`, `.bin`, or `.zip`)

## Running
```
./gradlew run
```

You can pass BIOS/ROM paths at launch:
```
./gradlew run -Pbios=/path/to/gba_bios.bin -Prom=/path/to/game.gba
```

`run` now defaults to BIOS SWI execution (`ygba.hle.swi=false`) for better intro stability.
You can force HLE SWIs back on if needed:
```
./gradlew run -Dygba.hle.swi=true
```

Debug logs launch (does not open debugger dialog):
```
./gradlew runDebug -Pbios=/path/to/gba_bios.bin -Prom=/path/to/game.gba
```

Debugger launch (opens debugger dialog + logs):
```
./gradlew runDebugger -Pbios=/path/to/gba_bios.bin -Prom=/path/to/game.gba
```

Debug launch with guaranteed fresh rebuild:
```
./gradlew runDebugFresh -Pbios=/path/to/gba_bios.bin -Prom=/path/to/game.gba
```

Debugger launch with guaranteed fresh rebuild:
```
./gradlew runDebuggerFresh -Pbios=/path/to/gba_bios.bin -Prom=/path/to/game.gba
```

Automated dump capture (clears `dumps/`, then captures every 250ms for 30s):
```
./gradlew captureDumps
```

Override capture interval/duration (ms):
```
./gradlew captureDumps -PdumpIntervalMs=250 -PdumpDurationMs=30000
```

Equivalent helper script:
```
./scripts/capture-dumps.sh 250 30000
```

Quick dump summary:
```
./scripts/analyze-dumps.sh
```

`runDebug` and `runDebugger` write emulator logs to:
```
build/logs/ygba-debug.log
```

Override log file path:
```
./gradlew runDebug -PlogFile=/tmp/ygba.log
```

Or directly via CLI args:
```
./gradlew run --args="--bios /path/to/gba_bios.bin --rom /path/to/game.gba --debugger --debug-console --status-log --trace-video --log-file /tmp/ygba.log"
```

Auto dump via CLI args:
```
./gradlew run --args="--auto-dump-interval-ms 250 --auto-dump-duration-ms 30000 --auto-dump-exit --status-log --trace-video --log-file build/logs/ygba-capture.log"
```

Enable video-register trace in debug task:
```
./gradlew runDebug -PtraceVideo
```

## Building
```
./gradlew build
```

## Controls

| GBA | Keyboard |
|-----|----------|
| A | X |
| B | C |
| L | S |
| R | D |
| Select | Space / Backspace |
| Start | Enter |
| D-Pad | Arrow keys |

Debug: press `]` to dump the current frame and emulator state.

## What works

- **CPU** — ARM7TDMI with 32-bit ARM and 16-bit THUMB instruction sets. Includes a step debugger with disassembly, register/flag inspection, and memory bank switching.
- **Graphics** — Modes 0–4, tiled and affine backgrounds, sprites with flip/mosaic, window and color-blend effects.
- **Memory** — System ROM, IO registers, palette/video/OAM RAM, cartridge SRAM, Flash (64K/128K), and EEPROM save types with auto-detection.
- **DMA** — All four channels with correct transfer timing and special-trigger support.
- **Timers** — Cascade and overflow-interrupt behavior.
- **Input** — Keyboard controls mapped above; interrupt-driven keypad support.
- **File formats** — `.gba`, `.agb`, `.bin`, and `.zip` ROMs.

## What doesn't work (yet)

- Sound — not emulated at all.
- Sprite affine transforms (rotate/scale).
- Window/blend edge-case accuracy.
- Cycle-accurate timing.
- Save states.
- Memory/IO/palette/map viewers (beyond the existing CPU debugger).

## Credits

Original emulator by **khaledev** — [YahGBA](https://github.com/khaledev/YahGBA) (2006).
