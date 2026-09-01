# Commit messages

Conventional Commits, enforced by `.githooks/commit-msg`:

```
type(scope): what the change makes true
```

- **Types:** `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `build`, `ci`.
- **Scope** is the area, not a directory path: `detail`, `activity`, `terminal`, `session`,
  `diff`, `tabs`. Omit it for repo-wide changes.
- **Subject** states the resulting behaviour, not the mechanism, in the imperative and under
  72 characters. No trailing period.
- **Body** only when the subject cannot carry it: the why and the consequence, never a
  file-by-file recap. A bug's root cause belongs here or in a test name, never in a comment.
- Never add an AI-attribution trailer.

The hook lives in the repository, so `git config core.hooksPath .githooks` is needed once per
clone. It is already set on this machine.
