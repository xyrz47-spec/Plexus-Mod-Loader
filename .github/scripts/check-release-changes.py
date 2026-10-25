"""Stops a release when nothing that goes into the build changed since the previous release tag.

A new build must differ from the last one. Website, documentation, test and workflow changes do not change the
installer or the SDK, so they are deployed with the website (`npm run deploy:site`) and wait for the next real
build in CHANGELOG.md's "## Unreleased" section. A file whose only change is the version number (the version
bump itself) does not count either.

Usage: python -I check-release-changes.py <version> [<ref>]   (ref defaults to HEAD; run from the repository root)
"""
import subprocess
import sys

version, ref = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'HEAD'

# Paths that never reach the installer or the SDK.
NOT_IN_BUILD = ('site/', 'docs/', 'examples/', '.claude/', '.github/')


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, encoding='utf-8', check=True).stdout


def show(rev, path):
    result = subprocess.run(['git', 'show', f'{rev}:{path}'], capture_output=True)
    return result.stdout if result.returncode == 0 else None


def in_build(path):
    return not (path.startswith(NOT_IN_BUILD) or path.endswith('.md') or '/src/test/' in path)


previous_tag = git('describe', '--tags', '--abbrev=0', '--match', 'v*', '--exclude', f'v{version}', ref).strip()
previous_version = previous_tag.removeprefix('v')
changed = [p for p in git('diff', '--name-only', previous_tag, ref).splitlines() if in_build(p)]


def only_version_changed(path):
    old, new = show(previous_tag, path), show(ref, path)
    return old is not None and new is not None and old.replace(previous_version.encode(), version.encode()) == new


build_changes = [p for p in changed if not only_version_changed(p)]
if not build_changes:
    sys.exit(
        f'Nothing that goes into the build changed since {previous_tag}, so {version} would be the same build.\n'
        'Deploy website or documentation changes with `npm run deploy:site` and keep them under "## Unreleased" '
        'in CHANGELOG.md for the next real build.'
    )
print(f'{len(build_changes)} file(s) in the build changed since {previous_tag}:')
for path in build_changes:
    print(f'  {path}')
