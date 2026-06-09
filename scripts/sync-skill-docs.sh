#!/usr/bin/env bash
#
# Vendor the canonical genius-uml skill docs into the gf-standards plugin.
#
# Source of truth:  docs/skill/{SKILL.md,reference/}  (this repo)
# Target:           plugins/gf-standards/skills/library-genius-uml/  (claude-marketplace)
#
# A plain copy — deliberately NOT a git submodule (a submodule is empty on plugin
# install and pins a stale commit). Run by hand after editing docs/skill/, or wire
# into CI to push the update on release. See the playbook:
#   claude-marketplace/plugins/gf-standards/docs/playbooks/library-skill-docs.md
#
# Usage:
#   ./scripts/sync-skill-docs.sh
#   GF_MARKETPLACE=/path/to/claude-marketplace ./scripts/sync-skill-docs.sh
#   ./scripts/sync-skill-docs.sh --check        # verify in sync, write nothing (exit 1 if drifted)

set -euo pipefail

skill_name="library-genius-uml"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="${repo_root}/docs/skill"

marketplace="${GF_MARKETPLACE:-${repo_root}/../claude-marketplace}"
skill_dir="${marketplace}/plugins/gf-standards/skills/${skill_name}"

check_only=false
[[ "${1:-}" == "--check" ]] && check_only=true

# --- preconditions -----------------------------------------------------------
[[ -f "${src}/SKILL.md" ]] || { echo "error: ${src}/SKILL.md not found" >&2; exit 1; }
if [[ ! -d "${marketplace}/plugins/gf-standards" ]]; then
  echo "error: gf-standards plugin not found under '${marketplace}'." >&2
  echo "       Set GF_MARKETPLACE to your claude-marketplace checkout." >&2
  exit 1
fi

# --- collect the source file list (SKILL.md + reference/*.md) -----------------
mapfile -t files < <(cd "${src}" && find SKILL.md reference -type f -name '*.md' | sort)

# --- --check mode: diff only, never write ------------------------------------
if "${check_only}"; then
  drift=0
  for f in "${files[@]}"; do
    if ! cmp -s "${src}/${f}" "${skill_dir}/${f}" 2>/dev/null; then
      echo "drift: ${f}"
      drift=1
    fi
  done
  if [[ -d "${skill_dir}" ]]; then
    while IFS= read -r f; do
      printf '%s\n' "${files[@]}" | grep -qxF "${f}" || { echo "stale:  ${f}"; drift=1; }
    done < <(cd "${skill_dir}" && find . -type f -name '*.md' | sed 's|^\./||' | sort)
  fi
  if [[ "${drift}" -eq 0 ]]; then echo "skill docs in sync."; else
    echo "skill docs OUT OF SYNC — run ./scripts/sync-skill-docs.sh" >&2; fi
  exit "${drift}"
fi

# --- sync: mirror source → target --------------------------------------------
rm -rf "${skill_dir}"
mkdir -p "${skill_dir}/reference"
for f in "${files[@]}"; do
  mkdir -p "${skill_dir}/$(dirname "${f}")"
  cp "${src}/${f}" "${skill_dir}/${f}"
done

echo "synced ${#files[@]} files → ${skill_dir}"
printf '  %s\n' "${files[@]}"
echo
echo "Next: review & commit the change in ${marketplace}"
