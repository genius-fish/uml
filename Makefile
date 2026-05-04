SHELL = /bin/bash

.PHONY: clean fix fmt compile prepare build test validate deps publishLocal \
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

test:
	./mill __.test.testForked

validate: fix fmt compile test

deps:
	./mill mill.scalalib.Dependency/showUpdates

# ---------------------------------------------------------------------------
# Examples — runs every `fish.genius.uml.examples.*` ZIOAppDefault.
# Set PLANTUML_AVAILABLE=1 to additionally write rendered SVG to the cwd.
# ---------------------------------------------------------------------------

EXAMPLES = \
	ArchimateExample \
	ActivityExample \
	SequenceExample

examples:
	@for ex in $(EXAMPLES); do \
		echo "=== Rendering $$ex ==="; \
		PLANTUML_AVAILABLE=1 ./mill examples.runMain fish.genius.uml.examples.$$ex || exit $$?; \
	done

examples-clean:
	rm -f *-example.svg *-example.png

# ---------------------------------------------------------------------------
# Publishing
# ---------------------------------------------------------------------------

publishLocal:
	./mill __.publishLocal
