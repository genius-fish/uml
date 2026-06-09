# genius-uml skill docs (source of truth)

This directory is the **canonical source** for the `library-genius-uml` skill
shipped by the `gf-standards` Claude Code plugin. The files here are authored and
reviewed next to the library code; a sync step vendors a copy into the
marketplace so the published plugin is self-contained.

```
docs/skill/
  SKILL.md            # the skill itself (frontmatter + lean overview + index)
  reference/*.md      # detailed per-area reference, loaded on demand
```

This is one instance of a reusable Genius Fish pattern; the canonical playbook is
`plugins/gf-standards/docs/playbooks/library-skill-docs.md` in
`genius-fish/claude-marketplace` (first implemented for `genius-fish/latex`).

## Editing

Update these in the same PR that changes the public API they describe. Keep
`SKILL.md` lean (overview + index); put detail in `reference/*.md` so the skill
loads cheaply and Claude reads depth only when needed (progressive disclosure).

## Syncing into the plugin

`SKILL.md` and `reference/` are vendored into the marketplace plugin at
`plugins/gf-standards/skills/library-genius-uml/` — **not** via a git submodule
(empty on plugin install, pins a stale commit).

### Automatic — on release

`.github/workflows/sync-skill-docs.yml` runs on every release tag (`v*`),
regenerates the skill from this directory, and pushes it to the
`genius-fish/claude-marketplace` `main` branch. Needs a `MARKETPLACE_SYNC_TOKEN`
repo secret (a token with **Contents: write** on the marketplace repo).

### Manual — anytime

```sh
./scripts/sync-skill-docs.sh
GF_MARKETPLACE=/path/to/claude-marketplace ./scripts/sync-skill-docs.sh
./scripts/sync-skill-docs.sh --check     # verify in sync (exit 1 if drifted)
```
