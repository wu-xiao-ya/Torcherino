# Torcherino Separate Diagnostics Log

Torcherino writes low-overhead JSON Lines diagnostics to:

```text
logs/torcherino-performance.log
```

Each line is one complete JSON object. The active file rotates by size to
`torcherino-performance.1.log`, `.2.log`, and later backups. Writing failure
disables only the separate diagnostics log and does not stop acceleration or
the server.

## Automatic events

- `session_start`: Torcherino, Minecraft, Forge, Java, target-mod versions,
  acceleration configuration, and a unique session id.
- `adapters`: enabled state, classification, structural signature, and probe
  result for every adapter.
- `summary`: one low-overhead aggregate at the configured wall-clock interval.
- `slow_manager`: a dimension exceeded `slowManagerMillis`.
- `slow_target`: one target exceeded `slowTargetMillis` after cooldown.
- `profile_snapshot`: detailed profiler rows written when a manual profile is
  stopped.
- `session_stop`: clean server shutdown.

Automatic summaries contain per-dimension torch, coverage, discovery, manager
time, target visit, requested/consumed/fallback/skipped virtual tick, random
block tick, adapter classification counters, and a grouped inventory of active
TileEntity classes and selected routes. They do not keep references to worlds,
TileEntities, inventories, capabilities, or item stacks.

## Commands

```text
/torcherino log status
/torcherino log snapshot
/torcherino log flush
/torcherino log mark <message>
```

`snapshot` immediately writes and resets the current interval counters.
`mark` adds an operator annotation, which is useful immediately before or
after a known lag event. Stopping `/torcherino profile` writes its top 50 rows
to the separate log automatically.

## Configuration

The existing `config/sci4me/Torcherino.cfg` gains:

```text
diagnostics {
    B:separateLogEnabled=true
    I:separateLogIntervalSeconds=60
    I:separateLogMaxSizeMb=16
    I:separateLogBackups=4
}
```

The automatic collector uses primitive counters and does not enable the
detailed profiler. Enable the profiler only while class-level timing is
needed.
