#!/usr/bin/env python3
"""Records, per session, which files and commits each tool call actually changed.

Runs as a Claude Code hook. PreToolUse stamps when the tool began; PostToolUse asks git which files
are dirty in every repository the call could have reached and keeps the ones modified inside the
call's own window, plus any commit HEAD moved over in that window. A file the user edited between
two calls falls outside every window and is never attributed. Any failure exits quietly: a hook
that broke the agent would cost more than the ledger is worth.
"""
import json
import os
import re
import subprocess
import sys
import time

CHANGE_DIRECTORY = re.compile(r"(?:^|[;&|(`]\s*|\s)cd\s+[\"']?([^\s;&|)\"']+)")
DASH_C = re.compile(r"\bgit\s+-C\s+[\"']?([^\s;&|)\"']+)")
ABSOLUTE = re.compile(r"(?<![\w./-])(/[^\s'\"`;&|<>()]+)")
PATH_KEYS = ("file_path", "notebook_path", "path")
WINDOW_SLACK_SECONDS = 1.0
STALE_PRE_SECONDS = 3600
GIT_TIMEOUT_SECONDS = 15


def main():
    data = json.loads(sys.stdin.read() or "{}")
    session = data.get("session_id")
    directory = os.environ.get("CANOPY_LEDGER_DIR")
    if not session or not directory:
        return

    os.makedirs(directory, exist_ok=True)
    state_path = os.path.join(directory, session + ".state.json")
    ledger_path = os.path.join(directory, session + ".ledger")
    state = load_state(state_path)
    event = data.get("hook_event_name")
    now = time.time()

    if event == "SessionStart":
        state.setdefault("started", now)
        for root in roots_for(data):
            baseline(state, root)
    elif event == "PreToolUse":
        pending = state.setdefault("pre", {})
        for key in [key for key, stamped in pending.items() if now - stamped > STALE_PRE_SECONDS]:
            del pending[key]
        pending[tool_key(data)] = now
    elif event == "PostToolUse":
        pre = state.setdefault("pre", {}).pop(tool_key(data), None) or state.get("last_post") or state.get("started", now)
        lines = []
        for root in roots_for(data):
            lines.extend(scan(state, root, pre - WINDOW_SLACK_SECONDS, now + WINDOW_SLACK_SECONDS, now))
        state["last_post"] = now
        if lines:
            with open(ledger_path, "a", encoding="utf-8") as ledger:
                ledger.write("".join(lines))
    else:
        return

    save_state(state_path, state)


def tool_key(data):
    return data.get("tool_use_id") or "_"


def roots_for(data):
    """Every repository this call could have written in: where it ran, what it named, where it cd'd."""
    cwd = data.get("cwd") or os.getcwd()
    candidates = [cwd]
    tool_input = data.get("tool_input") or {}
    for key in PATH_KEYS:
        value = tool_input.get(key)
        if isinstance(value, str) and value:
            candidates.append(value if os.path.isabs(value) else os.path.join(cwd, value))
    command = tool_input.get("command")
    if isinstance(command, str):
        for match in CHANGE_DIRECTORY.finditer(command):
            candidates.append(os.path.join(cwd, os.path.expanduser(match.group(1))))
        for match in DASH_C.finditer(command):
            candidates.append(os.path.join(cwd, os.path.expanduser(match.group(1))))
        candidates.extend(ABSOLUTE.findall(command))

    roots = []
    for candidate in candidates:
        root = git_root(os.path.normpath(candidate))
        if root and root not in roots:
            roots.append(root)
    return roots


def git_root(path):
    current = path if os.path.isdir(path) else os.path.dirname(path)
    while current and current != os.path.dirname(current):
        if os.path.exists(os.path.join(current, ".git")):
            return current
        current = os.path.dirname(current)
    return None


def baseline(state, root):
    entries = status(root)
    if entries is None:
        return
    state.setdefault("deleted", {})[root] = sorted(deleted_in(entries))
    head = git(root, "rev-parse", "HEAD")
    if head:
        state.setdefault("head", {})[root] = head


def scan(state, root, low, high, now):
    entries = status(root)
    if entries is None:
        return []
    stamp = str(int(now * 1000))
    lines = []

    for code, path, original in entries:
        if "D" in code:
            continue
        full = os.path.join(root, path)
        try:
            modified = os.lstat(full).st_mtime
        except OSError:
            continue
        if low <= modified <= high:
            lines.append(f"{stamp}\tW\t{root}\t{full}\n")

    deleted = deleted_in(entries)
    known = set(state.setdefault("deleted", {}).get(root, []))
    for path in sorted(deleted - known):
        lines.append(f"{stamp}\tD\t{root}\t{os.path.join(root, path)}\n")
    state["deleted"][root] = sorted(deleted)

    head = git(root, "rev-parse", "HEAD")
    previous = state.setdefault("head", {}).get(root)
    if head and previous and head != previous:
        for sha in commits_between(root, previous, head, low, high):
            lines.append(f"{stamp}\tC\t{root}\t{sha}\n")
    if head:
        state["head"][root] = head

    return lines


def deleted_in(entries):
    gone = set()
    for code, path, original in entries:
        if "D" in code:
            gone.add(path)
        if original:
            gone.add(original)
    return gone


def status(root):
    output = git(root, "status", "--porcelain=v1", "-z", "--untracked-files=all")
    if output is None:
        return None
    tokens = output.split("\0")
    entries = []
    index = 0
    while index < len(tokens):
        token = tokens[index]
        index += 1
        if len(token) < 4:
            continue
        code, path = token[:2], token[3:]
        original = None
        if code[0] in "RC" and index < len(tokens):
            original = tokens[index]
            index += 1
        entries.append((code, path, original))
    return entries


def commits_between(root, previous, head, low, high):
    """Commits HEAD moved over whose committer date sits in the window: a pull's history does not."""
    output = git(root, "log", "--format=%H %ct", f"{previous}..{head}")
    if not output:
        return []
    shas = []
    for line in output.splitlines():
        parts = line.split()
        if len(parts) != 2:
            continue
        if low <= float(parts[1]) <= high:
            shas.append(parts[0])
    return shas


def git(root, *args):
    try:
        result = subprocess.run(
            ["git", "-C", root, *args],
            capture_output=True,
            text=True,
            timeout=GIT_TIMEOUT_SECONDS,
            env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"},
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    return result.stdout.strip("\n") if args[0] != "status" else result.stdout


def load_state(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {}


def save_state(path, state):
    temporary = f"{path}.{os.getpid()}"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump(state, handle)
    os.replace(temporary, path)


if __name__ == "__main__":
    try:
        main()
    except Exception:  # noqa: BLE001 - never let the ledger break the agent
        pass
    sys.exit(0)
