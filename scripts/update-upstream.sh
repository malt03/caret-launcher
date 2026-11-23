#!/usr/bin/env bash
# Update code copied from AOSP to a new release tag (docs/launcher3-porting.md).
#
# Usage: scripts/update-upstream.sh <third_party dir> <new tag>
#   e.g. scripts/update-upstream.sh third_party/launcher3 android-17.0.0_r2
#
# Reads the ```upstream block of <dir>/UPSTREAM.md, fetches the old and the new tag, and
# three-way merges every listed file with `git merge-file` (ours = this repo, base = old tag,
# theirs = new tag). Files with conflicts keep conflict markers and are listed at the end.
# With `res_dir=<dir>` in the block, upstream paths under res/ are kept under <dir>/ here.
# `derived=<upstream path> <path from the repository root>` merges a stand-in that was made from
# an upstream file (by deleting what TextLauncher does not use and marking changes) the same way.
# After merging, scripts/check-upstream.py lists what else changed upstream for a person to
# review: the `ref=` files (stand-ins and transcriptions), the extracted resource values and the
# flags.
#
# The tag in UPSTREAM.md is only changed when every file merged without a conflict or an error,
# so the script can be run again after fixing the conflicts.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

dir=${1:?third_party dir}
new_tag=${2:?new tag}
manifest="$dir/UPSTREAM.md"

block=$(awk '/^```upstream$/{f=1;next} /^```$/{f=0} f' "$manifest")
repo=$(printf '%s\n' "$block" | sed -n 's/^repo=//p')
old_tag=$(printf '%s\n' "$block" | sed -n 's/^tag=//p')
res_dir=$(printf '%s\n' "$block" | sed -n 's/^res_dir=//p')

if [ "$old_tag" = "$new_tag" ]; then
    echo "$manifest is already at $new_tag" >&2
    exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for t in base:"$old_tag" theirs:"$new_tag"; do
    name=${t%%:*}; tag=${t#*:}
    if ! err=$(git clone -q --depth 1 --branch "$tag" "$repo" "$work/$name" 2>&1); then
        echo "error: cannot fetch $tag from $repo" >&2
        echo "$err" >&2
        exit 1
    fi
done

conflicts=()
errors=()
notes=()
touched=()

list_files() { # $1 = root, $2 = path
    if [ -d "$1/$2" ]; then (cd "$1" && find "$2" -type f); elif [ -f "$1/$2" ]; then echo "$2"; fi
}

# A file that is gone at the new tag may have moved: names of files with the same name.
moved_to() { # $1 = path
    (cd "$work/theirs" && find . -type f -name "$(basename "$1")" -not -path './.git/*' \
        | sed 's|^\./||' | head -5 | paste -sd ' ' -)
}

# Runs git merge-file; 0 = clean, 1-127 = conflicts (count), >= 128 = error.
merge() { # $1 = ours, $2 = base, $3 = theirs, $4 = name to report
    local rc=0
    git merge-file -q -L ours -L "$old_tag" -L "$new_tag" "$1" "$2" "$3" || rc=$?
    touched+=("$1")
    if [ "$rc" -ge 128 ]; then errors+=("$4: git merge-file failed ($rc)")
    elif [ "$rc" -gt 0 ]; then conflicts+=("$4")
    fi
}

while read -r p; do
    [ -n "$p" ] || continue
    while read -r f; do
        [ -n "$f" ] || continue
        base="$work/base/$f"; theirs="$work/theirs/$f"; local=$f
        if [ -n "$res_dir" ]; then local=${f/#res\//$res_dir/}; fi
        ours="$dir/$local"
        if [ -f "$base" ] && [ -f "$theirs" ]; then
            if [ -f "$ours" ]; then
                merge "$ours" "$base" "$theirs" "$f"
            else
                notes+=("not present here, skipped: $f")
            fi
        elif [ -f "$theirs" ]; then
            if [ -f "$ours" ]; then
                cmp -s "$ours" "$theirs" || errors+=("$f: added upstream, but a different file is already here")
            else
                mkdir -p "$(dirname "$ours")"; cp "$theirs" "$ours"; notes+=("added: $f")
            fi
        elif [ -f "$ours" ]; then
            to=$(moved_to "$f")
            if cmp -s "$ours" "$base"; then
                rm "$ours"; notes+=("removed: $f${to:+ (possibly moved to: $to)}")
            else
                errors+=("$f: removed upstream but modified here, kept${to:+ (possibly moved to: $to)}")
            fi
        fi
    done < <( { list_files "$work/base" "$p"; list_files "$work/theirs" "$p"; } | sort -u )
done < <(printf '%s\n' "$block" | sed -n 's/^path=//p')

while read -r up ours; do
    [ -n "$up" ] || continue
    base="$work/base/$up"; theirs="$work/theirs/$up"
    if [ ! -f "$base" ]; then
        errors+=("$ours: $up is not at $old_tag (the derived= line is wrong)"); continue
    fi
    if [ ! -f "$theirs" ]; then
        to=$(moved_to "$up")
        errors+=("$ours: $up is gone at $new_tag${to:+ (possibly moved to: $to)}"); continue
    fi
    merge "$ours" "$base" "$theirs" "$ours"
done < <(printf '%s\n' "$block" | sed -n 's/^derived=//p')

# Conflict markers left from an earlier run count as conflicts too.
for f in ${touched[@]+"${touched[@]}"}; do
    if grep -q '^<<<<<<< ours' "$f" 2>/dev/null; then
        case " ${conflicts[*]-} " in *" $f "*) ;; *) conflicts+=("$f (markers)") ;; esac
    fi
done

for n in ${notes[@]+"${notes[@]}"}; do echo "$n"; done

# What else changed upstream, for a person to review (not merged by this script).
echo
python3 scripts/check-upstream.py "$dir" --to "$new_tag" || true

status=0
if [ ${#errors[@]} -gt 0 ]; then
    echo; echo "errors:"; for e in "${errors[@]}"; do echo "  $e"; done
    status=1
fi
if [ ${#conflicts[@]} -gt 0 ]; then
    echo; echo "conflicts:"; for c in "${conflicts[@]}"; do echo "  $c"; done
    status=1
fi
if [ "$status" -ne 0 ]; then
    echo; echo "$manifest is left at $old_tag; fix the above and run again."
    exit 1
fi

# Only the tag= line of the upstream block.
awk -v old="$old_tag" -v new="$new_tag" '
    /^```upstream$/ { f = 1 } /^```$/ { f = 0 }
    f && $0 == "tag=" old { $0 = "tag=" new }
    { print }' "$manifest" > "$manifest.tmp" && mv "$manifest.tmp" "$manifest"
echo "updated $dir: $old_tag -> $new_tag"
