#!/usr/bin/env python3
"""Checks what scripts/update-upstream.sh does not merge (docs/launcher3-porting.md).

Usage:
  scripts/check-upstream.py <third_party dir>               # check against the recorded tag
  scripts/check-upstream.py <third_party dir> --to <tag>    # what changed upstream up to <tag>

Reads the ```upstream block of <dir>/UPSTREAM.md:

  ref=<upstream path> <path from the repository root>
      A file here that holds upstream code without being merged (a stand-in in compat/, a
      transcription in app/, or a hand-written build file for an Android.bp). With --to, the
      upstream diff is printed, so a person can carry the change over.
  values_root=<upstream res dir>            (in this block's repo)
  values_root=<repo url> <upstream res dir> (in another repo, at the same tag)
      Where the values extracted into <res_dir>/values*/ come from (a later root overrides an
      earlier one, as quickstep/res overrides res in the Launcher3QuickStep build). Every name there is compared
      with the upstream value in every qualifier (values, values-v31, values-night, ...): a value
      that differs, or an upstream qualifier that is missing here, is reported unless the comment
      just before it (or a comment inside a style) says "TextLauncher:". With --to, names whose
      upstream value changed are listed.
  release_repo=<repo url>, release_config=<name>
      build/release and the release whose flag values the code uses (its `inherits` are followed).
  flags_file=<path from the repository root> <aconfig package>
      A Flags stand-in whose `return true/false;` are the release values.
  flag=<aconfig package>.<flag name> ENABLED|DISABLED
      A flag whose value is folded into the code (no Flags method), with the value assumed.

Exits with 1 when the check against the recorded tag finds a difference.
Set UPSTREAM_CACHE to a directory to keep the fetched trees between runs.
"""

import difflib
import glob
import os
import re
import subprocess
import sys
import tempfile

ROOT = subprocess.run(['git', 'rev-parse', '--show-toplevel'], capture_output=True, text=True,
                      check=True).stdout.strip()
os.chdir(ROOT)
CACHE = os.environ.get('UPSTREAM_CACHE') or os.path.join(tempfile.gettempdir(),
                                                         'textlauncher-upstream')
LANG = re.compile(r'^(b\+.*|[a-z]{2,3}(-r[A-Z]{2})?)$')


def fetch(repo, tag):
    """A shallow clone of repo at tag, cached."""
    name = re.sub(r'[^A-Za-z0-9]+', '_', repo.split('googlesource.com/')[-1]) + '@' + tag
    path = os.path.join(CACHE, name)
    if not os.path.isdir(path):
        os.makedirs(CACHE, exist_ok=True)
        r = subprocess.run(['git', 'clone', '-q', '--depth', '1', '--branch', tag, repo, path],
                           capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f'error: cannot fetch {tag} from {repo}\n{r.stderr}')
    return path


def read_block(dir_):
    text = open(os.path.join(dir_, 'UPSTREAM.md'), encoding='utf-8').read()
    block = text.split('```upstream\n', 1)[1].split('\n```', 1)[0]
    entries = {}
    for line in block.splitlines():
        if '=' in line:
            k, v = line.split('=', 1)
            entries.setdefault(k, []).append(v.strip())
    return entries


# ---- values ----

ELEMENT = re.compile(
    r'<(string-array|integer-array|declare-styleable|color|dimen|integer|bool|string|array|style|'
    r'attr|fraction|item|plurals)(?=[\s/>])[^>]*?\bname="([^"]+)"[^>]*?(?:/>|>.*?</\1>)', re.S)


def parse_values(path):
    """(type, name) -> (normalized text, marked) for one values XML file."""
    s = open(path, encoding='utf-8').read()
    out = {}
    pos = 0
    last_comment = ''
    for m in ELEMENT.finditer(s):
        between = s[pos:m.start()]
        # A comment marks the elements that follow it, up to a blank line or another comment;
        # only a comment that nothing but space separates from the element counts.
        comments = list(re.finditer(r'<!--(.*?)-->', between, re.S))
        if comments and not between[comments[-1].end():].strip():
            last_comment = comments[-1].group(1)
        elif comments or '\n\n' in between or between.strip():
            last_comment = ''
        pos = m.end()
        typ, name, body = m.group(1), m.group(2), m.group(0)
        if typ == 'item':
            t = re.search(r'type="(\w+)"', body)
            typ = t.group(1) if t else 'item'
        typ = {'string-array': 'array', 'integer-array': 'array'}.get(typ, typ)
        marked = 'TextLauncher:' in last_comment or (
            typ in ('style', 'declare-styleable') and 'TextLauncher:' in body)
        flag = re.search(r'android:featureFlag="([^"]+)"', body[:body.find('>') + 1])
        key = (typ, name)
        if flag:
            # A value behind a resource feature flag (android:featureFlag): kept aside, and used
            # only when the flag is on in the release (upstream_values).
            out.setdefault(('flagged', key), []).append((flag.group(1), normalize(body)))
        else:
            out[key] = (normalize(body), marked)
    return out


def normalize(body):
    body = re.sub(r'<!--.*?-->', '', body, flags=re.S)
    body = re.sub(r'\s+', ' ', body)
    return re.sub(r'> <', '><', body).strip()


def values_tree(res_root):
    """qualifier -> {(type, name): (text, marked)} for a res directory."""
    tree = {}
    for d in glob.glob(os.path.join(res_root, 'values*')):
        q = os.path.basename(d)[len('values'):].lstrip('-')
        for f in glob.glob(os.path.join(d, '*.xml')):
            # overlayable.xml (IntentResolver's) names the values that overlays may change, with
            # empty <item>s that are not values.
            if os.path.basename(f) == 'overlayable.xml':
                continue
            for k, v in parse_values(f).items():
                tree.setdefault(q, {})[k] = v
    return tree


def color_files(res_root):
    """qualifier -> {color name: text} for res/color* state lists (named by file)."""
    tree = {}
    for d in glob.glob(os.path.join(res_root, 'color*')):
        q = os.path.basename(d)[len('color'):].lstrip('-')
        for f in glob.glob(os.path.join(d, '*.xml')):
            text = open(f, encoding='utf-8').read()
            tree.setdefault(q, {})[('color', os.path.splitext(os.path.basename(f))[0])] = (
                normalize(text), 'TextLauncher:' in text)
    return tree


def upstream_values(entries, tag):
    roots = []
    for v in entries.get('values_root', []):
        parts = v.split()
        repo, path = (entries['repo'][0], parts[0]) if len(parts) == 1 else parts
        roots.append(os.path.join(fetch(repo, tag), path))
    # Later roots override earlier ones, as resource overlays do (quickstep/res over res). A
    # value behind a resource feature flag overrides only when the flag is on in the release.
    merged = {}
    for r in roots:
        for tree in (values_tree(r), color_files(r)):
            for q, vals in tree.items():
                plain = {k: v for k, v in vals.items() if k[0] != 'flagged'}
                merged.setdefault(q, {}).update(plain)
                for (_, key), variants in ((k, v) for k, v in vals.items() if k[0] == 'flagged'):
                    for flag, text in variants:
                        if flag_on(entries, tag, flag):
                            merged[q][key] = (re.sub(r' android:featureFlag="[^"]+"', '', text), False)
    return merged


def flag_on(entries, tag, flag):
    """A resource feature flag ("pkg.name" or "!pkg.name") in the release."""
    if 'release_config' not in entries:
        return False
    negate = flag.startswith('!')
    package, name = flag.lstrip('!').rsplit('.', 1)
    return release_value(entries, tag, package, name)[0] != negate


def ours_values(dir_, entries):
    res = os.path.join(dir_, entries.get('res_dir', ['res'])[0])
    ours = values_tree(res)
    for q, vals in color_files(res).items():
        for k, v in vals.items():
            ours.setdefault(q, {}).setdefault(k, v)
    return ours


def check_values(dir_, entries, tag):
    if 'values_root' not in entries:
        return []
    up = upstream_values(entries, tag)
    ours = ours_values(dir_, entries)
    names = {k for vals in ours.values() for k in vals if k[0] != 'flagged'}
    marked = {k for vals in ours.values() for k, v in vals.items() if k[0] != 'flagged' and v[1]}
    problems = []
    for key in sorted(names):
        up_qs = {q for q, vals in up.items() if key in vals}
        if not up_qs:
            if key not in marked:
                problems.append(f'{key[0]}/{key[1]}: not upstream (mark it "TextLauncher:")')
            continue
        our_qs = {q for q, vals in ours.items() if key in vals}
        for q in sorted(up_qs - our_qs):
            # Strings are copied for the locales TextLauncher has; other locales are not checked.
            if LANG.match(q.split('-')[0]) or key in marked:
                continue
            problems.append(f'{key[0]}/{key[1]}: upstream has values-{q}, missing here')
        for q in sorted(our_qs & up_qs):
            text, m = ours[q][key]
            if text != up[q][key][0] and not m:
                problems.append(f'{key[0]}/{key[1]} (values{"-" + q if q else ""}): differs from upstream')
    return problems


def changed_values(dir_, entries, old, new):
    if 'values_root' not in entries:
        return []
    a, b = upstream_values(entries, old), upstream_values(entries, new)
    names = {k for vals in ours_values(dir_, entries).values() for k in vals if k[0] != 'flagged'}
    out = []
    for key in sorted(names):
        by_what = {}  # one line per kind of change, listing the qualifiers
        for q in sorted(set(a) | set(b)):
            va, vb = a.get(q, {}).get(key), b.get(q, {}).get(key)
            if (va and va[0]) != (vb and vb[0]):
                what = 'added' if not va else 'removed' if not vb else 'changed'
                by_what.setdefault(what, []).append('values' + ('-' + q if q else ''))
        for what, qs in by_what.items():
            out.append(f'{key[0]}/{key[1]}: {what} upstream in {", ".join(qs)}')
    return out


# ---- flags ----

def release_value(entries, tag, package, name):
    """(enabled, release config that sets it) following the inherits chain, or default off."""
    repo = fetch(entries['release_repo'][0], tag)
    config = entries['release_config'][0]
    seen = set()
    while config and config not in seen:
        seen.add(config)
        p = os.path.join(repo, 'aconfig', config, package, name + '_flag_values.textproto')
        if os.path.exists(p):
            return 'ENABLED' in open(p).read(), config
        rc = os.path.join(repo, 'release_configs', config + '.textproto')
        m = re.search(r'inherits:\s*"([^"]+)"', open(rc).read()) if os.path.exists(rc) else None
        config = m.group(1) if m else None
    return False, 'default'


def snake(name):
    return re.sub(r'([A-Z])', lambda m: '_' + m.group(1).lower(), name)


def flags(entries):
    """(package, flag name, value used here, where) for every flag recorded."""
    out = []
    for v in entries.get('flags_file', []):
        path, package = v.split()
        src = open(path, encoding='utf-8').read()
        for name, value in re.findall(
                r'public static boolean (\w+)\(\)\s*\{[^}]*?return (true|false);', src, re.S):
            out.append((package, snake(name), value == 'true', path))
    for v in entries.get('flag', []):
        full, value = v.split()[:2]
        package, name = full.rsplit('.', 1)
        out.append((package, name, value == 'ENABLED', 'flag='))
    return out


def check_flags(entries, tag):
    if 'release_config' not in entries:
        return []
    problems = []
    for package, name, value, where in flags(entries):
        rv, config = release_value(entries, tag, package, name)
        if rv != value:
            problems.append(f'{package}.{name}: {"on" if value else "off"} here ({where}), '
                            f'{"on" if rv else "off"} in the release ({config})')
    return problems


def changed_flags(entries, old, new):
    if 'release_config' not in entries:
        return []
    out = []
    for package, name, value, where in flags(entries):
        a, b = release_value(entries, old, package, name), release_value(entries, new, package, name)
        if a[0] != b[0]:
            out.append(f'{package}.{name}: {"on" if a[0] else "off"} -> {"on" if b[0] else "off"} '
                       f'({b[1]}; used in {where})')
    return out


# ---- refs ----

def check_refs(entries, tag):
    up = fetch(entries['repo'][0], tag)
    problems = []
    for v in entries.get('ref', []):
        upath, ours = v.split()
        if not os.path.exists(os.path.join(up, upath)):
            problems.append(f'ref {upath}: not at {tag}')
        if not os.path.exists(ours):
            problems.append(f'ref {ours}: not in this repository')
    return problems


IDENT = re.compile(r'\b[A-Za-z_][A-Za-z0-9_]{5,}\b')
COMMON = {'return', 'public', 'private', 'protected', 'static', 'final', 'override', 'boolean',
          'String', 'import', 'package', 'internal', 'context', 'Context', 'super_', 'object',
          'Override', 'Nullable', 'NonNull', 'getContext', 'getWidth', 'getHeight', 'length',
          'invalidate', 'toString', 'equals', 'hashCode'}


def changed_refs(entries, old, new):
    a, b = fetch(entries['repo'][0], old), fetch(entries['repo'][0], new)
    out = []
    for v in entries.get('ref', []):
        upath, ours = v.split()
        pa, pb = os.path.join(a, upath), os.path.join(b, upath)
        la = open(pa, encoding='utf-8', errors='replace').read().splitlines() if os.path.exists(pa) else []
        lb = open(pb, encoding='utf-8', errors='replace').read().splitlines() if os.path.exists(pb) else []
        if la == lb:
            continue
        if not lb:
            out.append(f'== {ours}: {upath} is gone at {new}')
            continue
        here = {l.strip() for l in open(ours, encoding='utf-8').read().splitlines() if l.strip()}
        diff = list(difflib.unified_diff(la, lb, f'{old}/{upath}', f'{new}/{upath}', lineterm='', n=3))
        hunks, cur = [], []
        for line in diff[2:]:
            if line.startswith('@@') and cur:
                hunks.append(cur)
                cur = []
            cur.append(line)
        if cur:
            hunks.append(cur)
        if os.path.basename(upath) == 'Android.bp' or upath.endswith(('.gradle.kts', '.xml')):
            # A build file or a manifest that a hand-written one here follows: all of it.
            kept = hunks
        elif os.path.splitext(upath)[1] == os.path.splitext(ours)[1]:
            # Keep only the hunks that touch code that is also here (or add code next to it).
            kept = [h for h in hunks
                    if any(l[1:].strip() in here for l in h[1:] if l[:1] in ' -' and l[1:].strip())]
        else:
            # A transcription into another language: hunks whose changed lines name something
            # (a method, field or constant) that is named here too.
            names = set(IDENT.findall('\n'.join(here))) - COMMON
            kept = [h for h in hunks
                    if any(set(IDENT.findall(l[1:])) & names for l in h[1:] if l[:1] in '+-')]
        if kept:
            out.append(f'== {ours} (from {upath})')
            out.extend(diff[:2] + [l for h in kept for l in h])
        else:
            out.append(f'== {ours}: {upath} changed only in code not used here')
    return out


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    dir_ = args[0]
    entries = read_block(dir_)
    tag = entries['tag'][0]
    if '--to' in args:
        new = args[args.index('--to') + 1]
        sections = [('flags whose release value changed', changed_flags(entries, tag, new)),
                    ('extracted values that changed upstream', changed_values(dir_, entries, tag, new)),
                    ('upstream changes in ref= files', changed_refs(entries, tag, new))]
        for title, lines in sections:
            if lines:
                print(f'---- {title} ({tag} -> {new}) ----')
                print('\n'.join(lines))
        return 0
    problems = check_refs(entries, tag) + check_values(dir_, entries, tag) + check_flags(entries, tag)
    for p in problems:
        print(p)
    if problems:
        print(f'{len(problems)} difference(s) from {tag} in {dir_}')
        return 1
    print(f'{dir_}: matches {tag}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
