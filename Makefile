SHELL = /bin/bash

.PHONY: clean fix fmt compile prepare build test validate deps publishLocal publish \
        release-prepare release-finalize release-abort release-status \
        examples examples-clean

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

clean:
	./mill clean

fix:
	./mill __.fix

fmt:
	./mill __.reformat

compile:
	./mill __.compile

prepare: fix fmt compile

build: prepare test

# Set BLENDER_AVAILABLE=1 to also run the scene builder in Blender, headless.
test:
	./mill __.test.testForked

validate: fix fmt compile test

deps:
	./mill mill.scalalib.Dependency/showUpdates

# ---------------------------------------------------------------------------
# Examples: DiagramExample writes a flat diagram as .tex, .svg and .png to
# out/diagram/examples; SceneExample writes a 3D scene's JSON and the Blender builder to
# out/scene/examples.
# ---------------------------------------------------------------------------

EXAMPLES = \
	DiagramExample \
	SceneExample

examples:
	@for ex in $(EXAMPLES); do \
		echo "=== $$ex ==="; \
		./mill examples.runMain fish.genius.uml.examples.$$ex || exit $$?; \
	done

examples-clean:
	rm -rf out/diagram/examples out/scene/examples

# ---------------------------------------------------------------------------
# Publishing
# ---------------------------------------------------------------------------

publishLocal:
	./mill __.publishLocal

# Publishes every PublishModule to the Genius Fish Maven repo.
# Requires REPO_USERNAME and REPO_TOKEN in the environment.
publish:
	@if [ -z "$$REPO_USERNAME" ] || [ -z "$$REPO_TOKEN" ]; then \
		echo "[ERROR] REPO_USERNAME and REPO_TOKEN must be set"; exit 1; \
	fi
	./mill mill.javalib.MavenPublishModule/ \
		--username "$$REPO_USERNAME" \
		--password "$$REPO_TOKEN" \
		--releaseUri https://repo.genius.fish/releases \
		--snapshotUri https://repo.genius.fish/snapshots

# ---------------------------------------------------------------------------
# Release (delegated to mill-release plugin)
# Validation and publishing run as separate Mill processes (cannot nest).
# Usage: make release-prepare TYPE=patch|minor|major
#        (review RELEASE_x_y_z.md)
#        make release-finalize
# ---------------------------------------------------------------------------

# The release path runs the full gate and stages NOTHING (GFSDK-2). Whatever fix/fmt changed is
# for a human to review and commit; mill-release's clean-tree check counts staged changes as
# dirty, so `git add -u` here only turned a reformat into "Uncommitted changes detected".
# A tree the gate left dirty stops before releasePrepare, with the files named.
release-prepare:
	@if [ -z "$(TYPE)" ]; then \
		echo "[ERROR] Usage: make release-prepare TYPE=patch|minor|major"; exit 1; \
	fi
	@if [ -n "$$(git status --porcelain | grep -v '^??')" ]; then \
		echo "[ERROR] The tree is not clean. Commit or stash before release-prepare:"; \
		git status --short | grep -v '^??'; exit 1; \
	fi
	./mill clean
	$(MAKE) validate
	@if [ -n "$$(git status --porcelain | grep -v '^??')" ]; then \
		echo "[ERROR] validate (fix/fmt) changed the tree. Nothing was staged."; \
		echo "[ERROR] Review and commit these changes, then run release-prepare again:"; \
		git status --short | grep -v '^??'; exit 1; \
	fi
	./mill release.releasePrepare --releaseType $(TYPE)

# Representative published artifact (no version) used by release-preflight to detect an already-
# published or partially-published version on the releases repo. Empty disables the check.
RELEASE_VERIFY_BASE ?= fish/genius/genius-uml-core_3

# Atomic release: releaseFinalize creates the tag LOCALLY (no push), we publish, then
# releasePushRelease pushes the tag. A failed publish leaves nothing public — recover with
# `make release-abort`, then retry the same version.
release-finalize:
	./mill release.releaseFinalize
	$(MAKE) release-preflight
	$(MAKE) publishLocal
	$(MAKE) publish
	./mill release.releasePushRelease

release-preflight:
	@VERSION=$$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//'); \
	if [ -z "$(RELEASE_VERIFY_BASE)" ]; then \
		echo "[WARN] RELEASE_VERIFY_BASE unset, skipping remote pre-flight"; \
	else \
		url="https://repo.genius.fish/releases/$(RELEASE_VERIFY_BASE)/$$VERSION/"; \
		code=$$(curl -s -o /dev/null -w "%{http_code}" -u "$$REPO_USERNAME:$$REPO_TOKEN" -I "$$url" 2>/dev/null || echo 000); \
		if [ "$$code" -ge 200 ] && [ "$$code" -lt 400 ]; then \
			echo "[ERROR] Version $$VERSION already on remote at $$url (HTTP $$code)."; \
			echo "        Aborting before publish to avoid a partial/burned release."; \
			echo "        Run 'make release-abort', clean any orphans, then bump the version."; \
			exit 1; \
		fi; \
		echo "[OK] Pre-flight: $$VERSION not present remotely (HTTP $$code)"; \
	fi

release-abort:
	./mill release.releaseAbort

release-status:
	./mill release.releaseStatus
