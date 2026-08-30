# Canopy

One view over every tree an agent works in.

Canopy runs Claude Code sessions inside your JetBrains IDE and, when they are done, lets you review
what they changed — across the project, its submodules and every git worktree — in a single diff tree.

## What it does

- **Detail** — one session's changes across every repository it wrote to, sectioned into uncommitted,
  committed, pushed and untracked. Files open in the IDE's own diff viewer.
- **Commit across repositories** — including the submodule pointer bump in the parent, which is what
  makes the change visible to anyone else.
- **Worktrees** — grouped under the repository that owns them, with the sessions that ran in each.
  Submodules and their worktrees are discovered from git, not from configuration.
- **Sessions** — blocked agents sort first, whether or not they were started from the IDE.
  `Cmd+Shift+O` opens the session palette, `Cmd+Shift+J` jumps to the next one waiting on you.

## Building

```
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew buildPlugin
```

The plugin lands in `build/distributions/`. Install it through
*Settings > Plugins > Install Plugin from Disk*, or run a sandbox IDE with `./gradlew runIde`.
