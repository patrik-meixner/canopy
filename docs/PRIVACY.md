# Privacy

Canopy Workspace collects nothing and sends nothing anywhere.

## What it reads

Everything it shows comes from files already on your machine:

- Claude Code's own transcripts and task lists, under `~/.claude`
- the git repositories your project is made of, through `git` on your `PATH`
- the plugin's own settings, in the IDE's configuration

## What it writes

- its settings, and the list of session tabs to reopen, in the IDE's configuration
- a temporary settings file per session, so the agent reports its state back to the IDE; it is
  your own Claude Code settings with a status line and hooks added, and it is deleted when the
  session closes
- checkpoints you ask for, as git refs inside your own repository

## What leaves your machine

Nothing the plugin does. It has no telemetry, no analytics, no crash reporting and no network
calls of its own.

The agent you run in it is a separate program with its own network behaviour and its own privacy
terms. Canopy Workspace starts it and reads what it wrote; it does not add to what it sends.
