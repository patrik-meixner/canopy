# Screenshots

`python3 tools/demo/seed_demo.py --force` builds the workspace, then open
`~/CanopyDemo` in the IDE. Everything below is what that workspace shows.

Before the first shot: dark theme, zoom the IDE one step (`Ctrl+Shift+A` →
"Zoom IDE In") so text survives Marketplace's downscaling, and close every tool
window that is not part of the shot.

## 1 — Sessions and a review, side by side

The one that has to carry the listing. Canopy on the left, a session open in the
middle, the review on the right.

1. Open the **Canopy** tool window and the **Session** tool window.
2. Click *reverse charge on EU invoices*.
3. Detail tab, sections expanded: Changes, Committed, Pushed, Unversioned last.
4. Frame all three panels.

Shows: the list, the glyphs, and a session's changes across two repositories.

## 2 — One session, several repositories

1. Same session, Detail tab.
2. Collapse everything except **Changes**.
3. Expand until `billing` and `storefront` are both visible under it.

Shows: what the pitch line means — one session, more than one working tree.

## 3 — Commits across the whole session

The demo has 16 commits in `billing` and 10 in `storefront`, the newest few from
the last few hours, so the tab is full and a range is worth selecting.

1. **Commits** tab.
2. Select the top commit, then shift-click three below it.
3. Let the file tree fill in.

Shows: multi-select and the union of what a range of commits did.

## 4 — Workspaces

1. **Workspaces** tab in the Canopy window.
2. Expand **Worktrees**: `feat+reverse-charge`, `fix+cart-selection`.
3. Right-click one, leave the context menu open.

Shows: worktrees grouped under the repository that owns them, and what can be
done to one.

## 5 — Messages

1. **Messages** tab.
2. Scroll so three or four cards are visible whole.

Shows: what a session was asked, as something you can read back.

## 6 — Context

1. **Context** tab.
2. Expand a level or two.

Shows: what the agent is actually running with.

## 7 — The terminal

1. Focus the session tab itself, full width.
2. Have an agent mid-turn if you can, so the tab glyph is spinning.

Shows: it is a real terminal, not a chat box.

## For the Marketplace listing

Order: 1, 2, 3, 4. The first is the one most people will look at.

- 16:10, at least 1280 wide.
- Same theme and zoom in all four.
- No real repository names, no tokens, no customer data in any pixel — the demo
  workspace exists so none of that has to be cropped out.

## Cleaning up

```
rm -rf ~/CanopyDemo ~/.claude/projects/-Users-$(whoami)-CanopyDemo
```
