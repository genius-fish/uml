SHELL = /bin/bash

.PHONY: clean fix fmt compile prepare build test validate deps publishLocal \
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
