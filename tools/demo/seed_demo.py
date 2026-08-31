#!/usr/bin/env python3
"""Builds a workspace Canopy has something to show: repositories, worktrees, and the
transcripts of agents that worked in them.

Everything lands under one root (default ~/CanopyDemo) and one transcript directory
derived from it, so nothing of yours is read or written.
"""

import argparse
import json
import os
import random
import shutil
import subprocess
import sys
from datetime import datetime, timedelta, timezone
import uuid
from pathlib import Path

AUTHOR = ("Dana Reyes", "dana@example.com")

WORKSPACE_RULES = {
    "CLAUDE.md": """# Vestra

Storefront, billing, and the infrastructure they run on. Each service is a submodule with its own
history; nothing is shared between them but the contract in `docs/`.

- Money is integer cents everywhere but the presentation layer.
- A price is assembled once, in `billing`, and never recomputed downstream.
- Tests name the behaviour they pin, not the function they call.
""",
    ".claude/rules/money.md": """# Money

Integer cents in the domain, formatted only at the edge. A float that reaches the database is a bug
that has not been noticed yet.
""",
    ".claude/rules/tests.md": """# Tests

A test is done when it has been seen to fail for the right reason. Break the code it covers, watch
it go red, then put the code back.
""",
    ".claude/rules/commits.md": """# Commits

Conventional Commits, English, no trailing period. The subject says what the behaviour is now, not
which files moved.
""",
    ".claude/skills/invoice-rules/SKILL.md": """---
name: invoice-rules
description: The VAT and reverse-charge rules an invoice has to satisfy, and where each one is enforced.
---

# Invoice rules

Reverse charge omits the VAT row entirely rather than printing it at zero. A rate of zero is not the
same thing: the row is absent, not empty.
""",
    ".claude/skills/release/SKILL.md": """---
name: release
description: How a service is cut, tagged and rolled out, and what has to be green before any of it.
---

# Release

The suite is green on the branch tip before a tag exists, never after.
""",
}
CLI_VERSION = "2.1.251"
MODEL = "claude-opus-5"


def git(repo: Path, *args: str, quiet: bool = True) -> str:
    env = dict(
        os.environ,
        GIT_AUTHOR_NAME=AUTHOR[0], GIT_AUTHOR_EMAIL=AUTHOR[1],
        GIT_COMMITTER_NAME=AUTHOR[0], GIT_COMMITTER_EMAIL=AUTHOR[1],
    )
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        env=env, capture_output=True, text=True, check=False,
    )
    if result.returncode != 0 and not quiet:
        print(f"git {' '.join(args)} failed: {result.stderr.strip()}", file=sys.stderr)
    return result.stdout.strip()


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def init_repo(path: Path, branch: str) -> None:
    path.mkdir(parents=True, exist_ok=True)
    git(path, "init", "-q", "-b", branch)
    git(path, "config", "user.name", AUTHOR[0])
    git(path, "config", "user.email", AUTHOR[1])


def commit(repo: Path, message: str, when: datetime) -> None:
    stamp = when.strftime("%Y-%m-%dT%H:%M:%S%z")
    git(repo, "add", "-A")
    subprocess.run(
        ["git", "-C", str(repo), "commit", "-q", "-m", message],
        env=dict(
            os.environ,
            GIT_AUTHOR_NAME=AUTHOR[0], GIT_AUTHOR_EMAIL=AUTHOR[1],
            GIT_COMMITTER_NAME=AUTHOR[0], GIT_COMMITTER_EMAIL=AUTHOR[1],
            GIT_AUTHOR_DATE=stamp, GIT_COMMITTER_DATE=stamp,
        ),
        capture_output=True, text=True, check=False,
    )


# ── the workspace ────────────────────────────────────────────────────────────

SERVICE_FILES = {
    "src/routes/checkout.ts": "export const checkout = async () => {\n  return { ok: true }\n}\n",
    "src/routes/index.ts": "export * from './checkout'\n",
    "src/pricing/vat.ts": "export const withVat = (net: number, rate: number) => net * (1 + rate)\n",
    "src/pricing/rounding.ts": "export const toCents = (value: number) => Math.round(value * 100) / 100\n",
    "test/vat.test.ts": "import { withVat } from '../src/pricing/vat'\n",
    "package.json": '{\n  "name": "@vestra/billing",\n  "version": "2.4.0"\n}\n',
    "README.md": "# billing\n\nInvoicing and price assembly.\n",
}

WEB_FILES = {
    "app/cart/CartSummary.tsx": "export const CartSummary = () => null\n",
    "app/cart/useCart.ts": "export const useCart = () => ({ items: [] })\n",
    "app/layout.tsx": "export default function Layout() {\n  return null\n}\n",
    "package.json": '{\n  "name": "@vestra/storefront",\n  "version": "5.1.2"\n}\n',
}

INFRA_FILES = {
    "terraform/main.tf": 'provider "aws" {\n  region = "eu-central-1"\n}\n',
    "terraform/billing.tf": 'module "billing" {\n  source = "./modules/service"\n}\n',
    "README.md": "# infra\n\nEverything that runs the platform.\n",
}

HISTORY = [
    ("billing", "feat(pricing): assemble a line's price from net, vat and rounding", 26),
    ("billing", "test(pricing): the rate table is data, so it gets a fixture", 25),
    ("storefront", "feat(cart): a cart that survives a reload", 24),
    ("infra", "chore(terraform): one module per service, none of them special", 23),
    ("billing", "fix(pricing): round once, at the end, not per component", 22),
    ("storefront", "feat(cart): summarise a cart without a round trip", 21),
    ("billing", "refactor(pricing): the rate table stops knowing about currencies", 20),
    ("storefront", "fix(cart): keep quantity edits from resetting the selection", 19),
    ("billing", "test(pricing): pin the rate table against the 2026 rules", 18),
    ("infra", "feat(terraform): the billing service gets its own queue", 16),
    ("billing", "fix(checkout): a failed payment leaves no half-written invoice", 15),
    ("storefront", "refactor(cart): quantity lives on the line, not beside it", 14),
    ("billing", "feat(invoice): number invoices per year, not per install", 13),
    ("billing", "refactor(checkout): one place decides what a checkout costs", 12),
    ("storefront", "fix(cart): an empty cart says so rather than rendering nothing", 11),
    ("infra", "chore(terraform): pin the provider so plans stop drifting", 10),
    ("billing", "test(checkout): the rejection path is the one worth pinning", 9),
    ("billing", "feat(checkout): reject a checkout the pricing cannot explain", 8),
    ("storefront", "feat(cart): show what a discount actually took off", 7),
    ("billing", "fix(invoice): a credit note points at what it credits", 6),
    ("storefront", "perf(cart): the summary stops recomputing on every keystroke", 5),
    ("billing", "fix(vat): reverse-charge lines carry no vat at all", 4),
    ("infra", "fix(terraform): the queue's dead letters are kept, not dropped", 3),
]

# Committed here rather than in the source repository, so they sit above the remote:
# the review tree needs a Committed section as well as a Pushed one.
UNPUSHED = [
    ("billing", "feat(vat): reverse charge needs to know who the customer is", 4),
    ("billing", "test(vat): a reverse-charge line carries no vat", 3),
    ("billing", "fix(vat): domestic lines are unaffected by any of this", 2),
    ("storefront", "fix(cart): selection is keyed on the line, not its position", 3),
    ("storefront", "test(cart): editing a quantity keeps the selection", 2),
]


def build_workspace(root: Path) -> dict:
    sources = root / ".sources"
    definitions = {
        "billing": SERVICE_FILES,
        "storefront": WEB_FILES,
        "infra": INFRA_FILES,
    }
    now = datetime.now(timezone.utc)

    # Built once as plain repositories, then cloned in as submodules: a session that spans a
    # superproject and its submodules is the shape Canopy exists for.
    for name, files in definitions.items():
        path = sources / name
        init_repo(path, "main")
        for relative, body in files.items():
            write(path / relative, body)
        commit(path, "chore: import the service as it stands", now - timedelta(days=30))

    for name, message, days_ago in HISTORY:
        path = sources / name
        target = sorted(definitions[name])[hash(message) % len(definitions[name])]
        file = path / target
        file.write_text(file.read_text() + f"\n// {message.split(': ')[1]}\n")
        commit(path, message, now - timedelta(days=days_ago, hours=random.randint(0, 9)))

    init_repo(root, "main")
    write(root / "README.md", "# Vestra\n\nStorefront, billing, and what runs them.\n")
    for relative, body in WORKSPACE_RULES.items():
        write(root / relative, body)
    write(root / ".gitignore", ".sources/\nworktrees/\n")
    commit(root, "chore: the workspace", now - timedelta(days=31))

    for name in definitions:
        subprocess.run(
            ["git", "-C", str(root), "-c", "protocol.file.allow=always",
             "submodule", "add", "-q", f"file://{sources / name}", name],
            capture_output=True, text=True, check=False,
        )
    commit(root, "chore: add the services as submodules", now - timedelta(days=30))

    repos = {name: root / name for name in definitions}

    for name, message, hours_ago in UNPUSHED:
        path = repos[name]
        target = sorted(definitions[name])[hash(message) % len(definitions[name])]
        file = path / target
        file.write_text(file.read_text() + f"\n// {message.split(': ')[1]}\n")
        commit(path, message, now - timedelta(hours=hours_ago, minutes=random.randint(0, 50)))

    for name, branch in (("billing", "feat/reverse-charge"), ("storefront", "fix/cart-selection")):
        worktree = root / "worktrees" / branch.replace("/", "+")
        git(repos[name], "worktree", "add", "-q", "-b", branch, str(worktree))
        target = sorted(definitions[name])[0]
        (worktree / target).write_text((worktree / target).read_text() + "\n// work in progress\n")
        commit(worktree, f"wip({branch.split('/')[1]}): first cut", now - timedelta(hours=5))

    # One worktree whose branch is already merged, which is what the sweep is for.
    merged = root / "worktrees" / "chore+pin-provider"
    git(repos["infra"], "worktree", "add", "-q", "-b", "chore/pin-provider", str(merged))
    git(repos["infra"], "merge", "-q", "--no-ff", "-m", "Merge chore/pin-provider", "chore/pin-provider")

    # And a directory git no longer knows about, which is the other thing it reports.
    orphan = root / "worktrees" / "feat+abandoned-idea"
    orphan.mkdir(parents=True, exist_ok=True)
    write(orphan / "README.md", "Left behind by a worktree that was removed.\n")

    # Uncommitted work, so the review tree has something in every section.
    billing = repos["billing"]
    (billing / "src/pricing/vat.ts").write_text(
        "export const withVat = (net: number, rate: number) =>\n"
        "  rate === 0 ? net : net * (1 + rate)\n"
    )
    write(billing / "src/pricing/reverseCharge.ts", "export const isReverseCharge = () => false\n")
    write(billing / "coverage/report.html", "<html><body>generated</body></html>\n")
    write(repos["storefront"] / "app/cart/CartSummary.tsx", "export const CartSummary = () => <div />\n")

    return repos


# ── the transcripts ──────────────────────────────────────────────────────────

def entry(
    kind: str,
    text: str,
    when: datetime,
    session_id: str,
    cwd: str,
    parent: str | None,
    branch: str = "main",
    tool: dict | None = None,
    image: str | None = None,
) -> tuple[str, str]:
    """One transcript line, in the shape the CLI writes and reads back."""
    message = {"role": kind, "content": [{"type": "text", "text": text}]}
    if image:
        message["content"].append({
            "type": "image",
            "source": {"type": "base64", "media_type": "image/png", "data": image},
        })
    if kind == "assistant":
        message |= {"id": f"msg_{uuid.uuid4().hex[:24]}", "type": "message", "model": MODEL}
    if tool:
        message["content"].append({
            "type": "tool_use",
            "id": f"toolu_{uuid.uuid4().hex[:24]}",
            "name": tool["name"],
            "input": tool["input"],
        })

    identifier = str(uuid.uuid4())
    record = {
        "parentUuid": parent,
        "isSidechain": False,
        "userType": "external",
        "cwd": cwd,
        "sessionId": session_id,
        "version": CLI_VERSION,
        "gitBranch": branch,
        "type": kind,
        "message": message,
        "uuid": identifier,
        "timestamp": when.isoformat().replace("+00:00", "Z"),
    }

    return json.dumps(record), identifier


def edit(path: Path) -> dict:
    return {"name": "Edit", "input": {"file_path": str(path)}}


def read(path: Path) -> dict:
    return {"name": "Read", "input": {"file_path": str(path)}}


def grep(pattern: str, path: Path) -> dict:
    return {"name": "Grep", "input": {"pattern": pattern, "path": str(path)}}


def bash(command: str) -> dict:
    return {"name": "Bash", "input": {"command": command}}


def write_tool(path: Path) -> dict:
    return {"name": "Write", "input": {"file_path": str(path)}}


CONVERSATIONS = [
    {
        "name": "reverse charge on EU invoices",
        "branch": "feat/reverse-charge",
        "minutes_ago": 6,
        "tasks": [
            ("Find where the rate is chosen", "completed"),
            ("Make withVat return net when the charge is reversed", "completed"),
            ("Decide what the invoice prints instead of a zero VAT row", "in_progress"),
            ("Cover it with a test", "pending"),
        ],
        "turns": [
            ("user", "Invoices to EU businesses still carry VAT. They shouldn't - reverse charge applies.", None),
            ("assistant", "Reading how the rate is chosen before I change anything.",
             ("read", "billing", "src/pricing/vat.ts")),
            ("assistant", "Nothing in the rate table knows who the customer is, so the caller has to say.",
             ("grep", "billing", "src/pricing")),
            ("assistant", "Added `isReverseCharge` and made `withVat` return the net untouched when it holds.",
             ("edit", "billing", "src/pricing/reverseCharge.ts")),
            ("user", "Does that leave the total wrong anywhere else?", None),
            ("assistant", "Checkout sums the lines, so it follows. The invoice PDF prints a VAT row that would "
                          "now read zero - worth a line saying why, rather than a silent 0.",
             ("edit", "billing", "src/routes/checkout.ts")),
            ("assistant", "Tests pass, and both mutants died: dropping the guard fails the reverse-charge case, "
                          "inverting it fails the domestic one.",
             ("bash", "npm test -- pricing")),
        ],
    },
    {
        "name": "cart selection resets on quantity edit",
        "branch": "fix/cart-selection",
        "minutes_ago": 34,
        "tasks": [
            ("Reproduce the reset", "completed"),
            ("Key the selection on line id", "completed"),
            ("Check the keyboard path too", "completed"),
        ],
        "turns": [
            ("user", "Editing a quantity clears which items were selected. Reproduces every time - here it is.",
             ("screenshot",)),
            ("assistant", "The list rebuilds its rows on every cart change, and selection is held by row index.",
             ("read", "storefront", "app/cart/useCart.ts")),
            ("assistant", "Keyed the selection on line id instead. Editing a quantity now leaves it alone.",
             ("edit", "storefront", "app/cart/CartSummary.tsx")),
            ("assistant", "The keyboard path went through the same index, so it had the same bug. Both covered.",
             ("write", "storefront", "app/cart/lineKey.ts")),
        ],
    },
    {
        "name": "why does the plan keep drifting",
        "branch": "main",
        "minutes_ago": 128,
        "tasks": [
            ("Find what is unpinned", "completed"),
            ("Pin it and prove the plan is empty", "completed"),
        ],
        "turns": [
            ("user", "terraform plan shows changes nobody made. Again.", None),
            ("assistant", "The provider is unpinned, so every run resolves a newer one.",
             ("read", "infra", "terraform/main.tf")),
            ("assistant", "Pinned it. The next plan should be empty; if it is not, the drift is real.",
             ("edit", "infra", "terraform/main.tf")),
            ("assistant", "Empty plan, twice in a row.", ("bash", "terraform plan -detailed-exitcode")),
        ],
    },
    {
        "name": "round once, not per component",
        "branch": "main",
        "minutes_ago": 295,
        "tasks": [
            ("Find where each component rounds", "completed"),
            ("Round on the total instead", "completed"),
            ("Pin the 2026 rate table", "completed"),
        ],
        "turns": [
            ("user", "Totals are off by a cent on about one invoice in thirty.", None),
            ("assistant", "Each component rounds itself before the sum, so the error compounds.",
             ("read", "billing", "src/pricing/rounding.ts")),
            ("assistant", "Rounding happens once, on the total. The 2026 rate table is pinned by a test now.",
             ("edit", "billing", "test/vat.test.ts")),
        ],
    },
    {
        "name": "checkout rejects what it cannot explain",
        "branch": "main",
        "minutes_ago": 1500,
        "tasks": [("Add the guard and a reason", "completed")],
        "turns": [
            ("user", "If pricing can't explain a line, checkout should refuse it rather than guess.", None),
            ("assistant", "Added the guard and a reason on the failure, so support can read what happened.",
             ("edit", "billing", "src/routes/checkout.ts")),
        ],
    },
]


def tool_for(kind: str, argument, repos: dict) -> dict:
    if kind == "bash":
        return bash(argument)

    repo, relative = argument
    path = repos[repo] / relative

    if kind == "grep":
        return grep("reverse|vat|rate", path)
    if kind == "read":
        return read(path)
    if kind == "write":
        return write_tool(path)

    return edit(path)


def screenshot() -> str:
    """A small PNG standing in for a pasted screenshot, built rather than shipped."""
    import base64
    import struct
    import zlib

    width, height = 320, 180
    rows = b""
    for y in range(height):
        row = b"\x00"
        for x in range(width):
            band = y * 255 // height
            row += bytes((32 + band // 3, 26 + band // 4, 40 + band // 2))
        rows += row

    def chunk(kind: bytes, payload: bytes) -> bytes:
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows))
        + chunk(b"IEND", b"")
    )

    return base64.b64encode(png).decode()


def build_tasks(session_id: str, tasks: list) -> None:
    """The task list lives beside the transcript, one file per task, as the CLI keeps it."""
    directory = Path.home() / ".claude" / "tasks" / session_id
    directory.mkdir(parents=True, exist_ok=True)

    for number, (subject, status) in enumerate(tasks, start=1):
        (directory / f"{number}.json").write_text(json.dumps({
            "id": str(number),
            "subject": subject,
            "description": "",
            "activeForm": subject[0].lower() + subject[1:],
            "status": status,
            "blockedBy": [],
        }, indent=2))


def build_transcripts(root: Path, repos: dict, transcripts: Path) -> list:
    if transcripts.exists():
        shutil.rmtree(transcripts)
    transcripts.mkdir(parents=True)

    now = datetime.now(timezone.utc)
    written = []

    for conversation in CONVERSATIONS:
        session_id = str(uuid.uuid4())
        started = now - timedelta(minutes=conversation["minutes_ago"] + 9 * len(conversation["turns"]))
        lines = [json.dumps({"type": "custom-title", "customTitle": conversation["name"]})]
        at = started
        parent = None

        for kind, text, touched in conversation["turns"]:
            kind_of_tool = touched[0] if touched else None
            tool = None
            if touched and kind_of_tool not in ("screenshot",):
                tool = tool_for(kind_of_tool, touched[1] if kind_of_tool == "bash" else touched[1:], repos)
            line, parent = entry(
                kind, text, at, session_id, str(root), parent, conversation["branch"], tool,
                image=screenshot() if kind_of_tool == "screenshot" else None,
            )
            lines.append(line)
            at += timedelta(minutes=random.randint(2, 7))

        (transcripts / f"{session_id}.jsonl").write_text("\n".join(lines) + "\n")
        build_tasks(session_id, conversation["tasks"])
        written.append((conversation["name"], session_id))

    return written


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path.home() / "CanopyDemo"))
    parser.add_argument("--force", action="store_true", help="replace an existing demo root")
    parser.add_argument("--transcripts-only", action="store_true",
                        help="rewrite the transcripts of an existing workspace")
    arguments = parser.parse_args()

    root = Path(arguments.root).expanduser().resolve()
    # The CLI encodes both separators and dots, and writes only there; matching it keeps the
    # seeded sessions and any real one in the same directory.
    transcripts = Path.home() / ".claude" / "projects" / str(root).replace("/", "-").replace(".", "-")

    if arguments.transcripts_only:
        random.seed(7)
        repos = {name: root / name for name in ("billing", "storefront", "infra")}
        for name, session_id in build_transcripts(root, repos, transcripts):
            print(f"  {name}")
        return 0

    if root.exists():
        if not arguments.force:
            print(f"{root} exists. Pass --force to replace it.", file=sys.stderr)
            return 1
        shutil.rmtree(root)

    random.seed(7)
    root.mkdir(parents=True)
    repos = build_workspace(root)

    written = build_transcripts(root, repos, transcripts)

    print(f"Workspace:   {root}")
    print(f"Transcripts: {transcripts}")
    print()
    print("Sessions:")
    for name, session_id in written:
        print(f"  {name}")
    print()
    for name, path in repos.items():
        total = subprocess.run(["git", "-C", str(path), "rev-list", "--count", "HEAD"],
                               capture_output=True, text=True).stdout.strip()
        ahead = subprocess.run(["git", "-C", str(path), "rev-list", "--count", "origin/main..HEAD"],
                               capture_output=True, text=True).stdout.strip()
        print(f"  {name}: {total} commits, {ahead} of them unpushed")
    print()
    print(f"Open {root} in the IDE — the services are submodules of it.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
