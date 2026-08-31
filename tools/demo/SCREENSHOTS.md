# Screenshots

`python3 tools/demo/seed_demo.py --force` builds the workspace, then open
`~/CanopyDemo` in the IDE. Everything below is what that workspace shows.

What the demo has, so every panel has something real in it:

- three services as submodules of one superproject, ~30 commits between them,
  the newest few from the last hours and some of them unpushed
- four worktrees: two being worked in, one whose branch is already merged, and
  one directory git no longer knows about
- five sessions, renamed the way `/rename` renames them, one carrying a pasted
  screenshot, each with its own task list
- uncommitted work, untracked output, and a `.claude` with rules and skills

Before the first shot: dark theme, zoom the IDE one step (`Ctrl+Shift+A` →
"Zoom IDE In") so text survives Marketplace's downscaling, and close every tool
window that is not part of the shot.

Set each scene, then take it with:

```
/tmp/shotenv/bin/python tools/demo/shot.py 01-a-session-and-its-review
```

The window does not need to be in front — the capture finds it by title.

## 1 — A session and its review

The one that has to carry the listing.

1. Canopy on the left, a session open, the Session window on the right.
2. Detail tab, sections expanded.
3. Best taken while an agent is mid-turn, so the glyph is spinning.

## 2 — Worktrees and branches

1. **Workspaces** tab.
2. Expand **Worktrees**, and one worktree under it, so its sessions show.

Shows worktrees grouped under the repository that owns them, with the sessions
that ran in each.

## 3 — Commits across a session

1. **Commits** tab.
2. Select the top commit, then shift-click three below it.

Shows the union of what a range of commits did.

## 4 — Messages

1. **Messages** tab.
2. Scroll to the message carrying the screenshot.

## 5 — Plan

1. **Plan** tab, on the reverse-charge session.

Shows a task list mid-flight: some done, one running, one still waiting.

## 6 — Context

1. **Context** tab.
2. Expand **Project** — the workspace's own rules and skills.

Do not expand **Personal**: those are the rules on the machine taking the shot.

## 7 — Activity

1. **Activity** tab. Shows what the agent actually ran, in order.

## 8 — The terminal

1. Focus the session tab itself, full width, agent mid-turn.

Shows it is a real terminal, not a chat box.

## For the Marketplace listing

Order: 1, 2, 3, 6. The first is the one most people will look at.

- 16:10, at least 1280 wide.
- Same theme and zoom in all of them.
- No real repository names, no tokens, no customer data in any pixel — the demo
  workspace exists so none of that has to be cropped out.

## Cleaning up

```
rm -rf ~/CanopyDemo ~/.claude/projects/-Users-$(whoami)-CanopyDemo
```
