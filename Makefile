SHELL = /bin/bash

.PHONY: clean fix fmt compile prepare build test validate deps publishLocal publish \
        release-prepare release-finalize release-abort release-status \
        examples examples-eps examples-png examples-latex examples-clean

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

test:
	./mill __.test.testForked

validate: fix fmt compile test

deps:
	./mill mill.scalalib.Dependency/showUpdates

# ---------------------------------------------------------------------------
# Examples — runs every `fish.genius.uml.examples.*` ZIOAppDefault.
# Set PLANTUML_AVAILABLE=1 to additionally render through the live engine.
# Set PLANTUML_FORMAT=svg|eps|png|latex|latex_no_preamble to pick the format.
# ---------------------------------------------------------------------------

EXAMPLES = \
	ArchimateExample \
	ActivityExample \
	SequenceExample

# $(call render-examples,<format-label>) — runs every example with
# PLANTUML_AVAILABLE=1 and the given PLANTUML_FORMAT (lowercase).
define render-examples
	@for ex in $(EXAMPLES); do \
		echo "=== Rendering $$ex ($(1)) ==="; \
		PLANTUML_AVAILABLE=1 PLANTUML_FORMAT=$(1) \
			./mill examples.runMain fish.genius.uml.examples.$$ex || exit $$?; \
	done
endef

examples:
	$(call render-examples,svg)

examples-eps:
	$(call render-examples,eps)

examples-png:
	$(call render-examples,png)

examples-latex:
	$(call render-examples,latex)

examples-clean:
	rm -f *-example.svg *-example.png *-example.eps *-example.tex *-example.atxt *-example.utxt

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

release-prepare:
	@if [ -z "$(TYPE)" ]; then \
		echo "[ERROR] Usage: make release-prepare TYPE=patch|minor|major"; exit 1; \
	fi
	./mill clean
	$(MAKE) validate
	@if [ -n "$$(git status --porcelain | grep -v '^??')" ]; then \
		echo "[WARN] Validation made formatting changes, staging them"; \
		git add -u; \
	fi
	./mill release.releasePrepare --releaseType $(TYPE)

release-finalize:
	./mill release.releaseFinalize
	$(MAKE) publishLocal

release-abort:
	./mill release.releaseAbort

release-status:
	./mill release.releaseStatus
