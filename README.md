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

## Why not a terminal, or another agent workspace

Canopy is not competing with the tools below. It sits in a different place in the same workflow,
and it is worth being explicit about which one you actually need.

**Modern terminals and multiplexers** — Warp, Termic, tmux — make the place an agent runs in
better: blocks, history, panes, sharing. Canopy's question starts a moment later. When the agent
stops, a terminal cannot tell you which repositories it wrote to, whether the submodule pointer
still needs bumping, or which of your four sessions is blocked on a permission prompt. If your
agents run in one repository and you review with `git diff`, a good terminal may be all you need.

**Agent workspaces and orchestrators** — Orca, Superset — are built to run many agents, often in
their own environment, with their own conventions for isolation. Canopy deliberately does not own
the environment. It runs agents in the IDE you already have the project open in, with your run
configurations, your debugger, your diff viewer and your VCS setup, and it treats a git worktree as
the isolation primitive rather than inventing one.

**Editors with an agent built in** — Zed, and the assistant panes in most IDEs — put the agent
beside the code, which is the right instinct. The difference is the unit of work. Those panes are
scoped to one project root; Canopy's unit is a session, and a session's changes routinely land in a
superproject, two submodules and a worktree of one of them. Reviewing that as one changeset, and
committing it in the right order, is the thing Canopy exists for.

**Claude Code itself** is not being replaced. Canopy drives the real `claude` binary, so hooks,
MCP servers, skills, permissions and settings apply exactly as they do in your terminal. There is
no second agent loop to keep in sync, and nothing to migrate if you stop using the plugin.

> [!NOTE]
> The claims above are about categories, not feature checklists. Those tools move quickly and any
> list of what they cannot do would be wrong within a month.

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
