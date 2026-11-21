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
set -euo pipefail

dir=${1:?third_party dir}
new_tag=${2:?new tag}
manifest="$dir/UPSTREAM.md"

block=$(awk '/^```upstream$/{f=1;next} /^```$/{f=0} f' "$manifest")
repo=$(printf '%s\n' "$block" | sed -n 's/^repo=//p')
old_tag=$(printf '%s\n' "$block" | sed -n 's/^tag=//p')
paths=$(printf '%s\n' "$block" | sed -n 's/^path=//p')
res_dir=$(printf '%s\n' "$block" | sed -n 's/^res_dir=//p')

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git clone -q --depth 1 --branch "$old_tag" "$repo" "$work/base" 2>/dev/null
git clone -q --depth 1 --branch "$new_tag" "$repo" "$work/theirs" 2>/dev/null

conflicts=()
notes=()
list_files() { # $1 = root, $2 = path
    if [ -d "$1/$2" ]; then (cd "$1" && find "$2" -type f); elif [ -f "$1/$2" ]; then echo "$2"; fi
}

for p in $paths; do
    files=$( { list_files "$work/base" "$p"; list_files "$work/theirs" "$p"; } | sort -u )
    for f in $files; do
        base="$work/base/$f"; theirs="$work/theirs/$f"; local=$f
        if [ -n "$res_dir" ]; then local=${f/#res\//$res_dir/}; fi
        ours="$dir/$local"
        if [ -f "$base" ] && [ -f "$theirs" ]; then
            if [ -f "$ours" ]; then
                if ! git merge-file -q -L ours -L "$old_tag" -L "$new_tag" "$ours" "$base" "$theirs"; then
                    conflicts+=("$f")
                fi
            else
                notes+=("not present here, skipped: $f")
            fi
        elif [ -f "$theirs" ]; then
            mkdir -p "$(dirname "$ours")"; cp "$theirs" "$ours"; notes+=("added: $f")
        elif [ -f "$ours" ]; then
            if cmp -s "$ours" "$base"; then rm "$ours"; notes+=("removed: $f")
            else notes+=("removed upstream but modified here, kept: $f"); fi
        fi
    done
done

sed -i.bak "s/^tag=$old_tag\$/tag=$new_tag/" "$manifest" && rm "$manifest.bak"

for n in ${notes[@]+"${notes[@]}"}; do echo "$n"; done
if [ ${#conflicts[@]} -gt 0 ]; then
    echo "conflicts:"; for c in "${conflicts[@]}"; do echo "  $c"; done
    exit 1
fi
echo "updated $dir: $old_tag -> $new_tag"
