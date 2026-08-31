<div align="center">

<img src="src/main/resources/META-INF/pluginIcon.svg" width="96" alt="Canopy">

# Canopy

**One view over every tree an agent works in.**

Run Claude Code sessions inside your JetBrains IDE, then review what they changed across the
project, its submodules and every git worktree, in a single diff tree.

</div>

## Why

Running one agent is easy. Running four is not.

Each one lives in its own terminal, in its own directory, sometimes in its own worktree of a
submodule. When they stop, the work is scattered across repositories the IDE's Commit window
treats as separate universes, and the only record of what happened is a terminal that has already
scrolled past it. You end up with four terminals, no idea which one is blocked, and a review that
means opening three IDE windows.

Canopy is the missing half of that workflow: it keeps the sessions, and it keeps the review.

## What it does

### Review across every repository a session touched

The **Detail** tab is IntelliJ's Commit window, except it spans the superproject, its submodules
and their worktrees at once. Changes are sectioned by how far they have travelled: uncommitted,
committed but unpushed, pushed, untracked.

- **Commit everywhere at once**, including the submodule pointer bump in the parent, which is the
  step that makes a submodule change visible to anyone who clones.
- **Push from the same place**, submodules first, because a pointer commit references revisions
  that have to reach the remote before it does. Both GitLab and GitHub print the merge or pull
  request link in their push output, so the link needs no API token.
- Attribution is mined from **shell commands as well as edit tools** — an agent edits through
  heredocs and `sed` far more often than through `Edit`, and a session that did would otherwise
  look like it touched nothing.

### Know which agent wants you

Sessions sort by what they need, not just by recency: waiting for permission first, then waiting
for your reply, then working. A blocked session can be answered **without opening its tab** —
approve a permission prompt or type a reply straight from the list.

`Cmd+Shift+J` jumps to the next session that is blocked on you. `Cmd+Shift+O` opens the palette.

### Worktrees and submodules, properly

- Worktrees grouped under the repository that owns them, including each submodule's own, alongside
  a **branch tree** saying which worktree holds a branch, how far it has drifted, and whether its
  remote is gone.
- **Set Up Worktree** carries the files git deliberately ignores — `.env`, a `devConfig-local.ts`
  in one submodule, whatever each repository needs — and then runs its install command. The list
  is configured per repository, so a superproject and its submodules do not share one.
- **Disk usage** per worktree, on demand, because a handful of stale ones with installed
  dependencies is the usual reason a disk fills.

### The session's own window

Everything about the session on screen, beside its terminal:

| Tab | What it answers |
| --- | --- |
| **Detail** | What did it change, everywhere |
| **Context** | Which rules, agents, skills and memory files it loaded, and from which directory |
| **Plan** | Every task on its list, with status — not the handful the CLI has room to print |
| **Messages** | What you asked it, with images, searchable, clickable back into the terminal |
| **Activity** | What it has been doing, folded into runs, with a writes-only filter |

### Terminal parity

Clickable links and `Cmd+V` image paste in session terminals, which the CLI enables only for
terminals that identify themselves as JediTerm.

## Install

Download the latest `.zip` from [Releases](https://github.com/patrik-meixner/canopy/releases), then
*Settings → Plugins → ⚙ → Install Plugin from Disk*.

To build it yourself:

```bash
./gradlew buildPlugin      # lands in build/distributions/
./gradlew runIde           # sandbox IDE with the plugin loaded
```

Requires JDK 21 and IntelliJ IDEA 2024.3 or newer.

> [!NOTE]
> Canopy drives the `claude` CLI; it does not reimplement it. Anything you have configured for
> Claude Code — settings, hooks, MCP servers, skills — applies unchanged.

## Configure

*Settings → Tools → Canopy.* The defaults are deliberate; these are the ones worth knowing:

| Setting | Why you would change it |
| --- | --- |
| Notify when an agent needs permission | On. Turn-end notifications are separate and off, because an agent pauses between every batch of tool calls |
| Session tab chrome | Toolbar, git row and context bar can each be hidden when you read the same numbers from Claude Code's own status line |
| Carry these files into a new worktree | Per repository, with a Detect button that lists what git ignores near the root |
| Show sessions from the last _n_ days | Older ones stay one click away, and search always looks through all of them |

## Performance

The plugin is expected to sit in an IDE all day, so the work it does is bounded on purpose:

- Parsed transcripts are cached on size and modification time, so a gigabyte of history is read
  once and survives restarts.
- The session window's five tabs share **one** incremental transcript reader, which ticks only
  while a tab is on screen and only re-renders when something drawable actually changed.
- Diff content is fetched when a file is opened, not when the tree is built.
- Git sweeps are throttled, single-flighted, and skipped entirely while their panel is hidden.
