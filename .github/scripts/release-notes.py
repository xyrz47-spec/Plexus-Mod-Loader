"""Prints the CHANGELOG.md section of one version: from "## <version>" to the next "## <number>" heading.

The section must start with two short summaries, "### Highlights" (English) and "### 주요 변경" (Korean), a few
bullets each. They become the "## Highlights" and "## 주요 변경" sections of the release notes, which the
website's download page shows next to each build; the rest of the section follows under "## Full changes".

Usage: python release-notes.py <version> <CHANGELOG.md>
Environment: POLY_MINECRAFT_VERSIONS (comma-separated) adds a "Minecraft: ..." line first.
"""
import os
import re
import sys

version, changelog = sys.argv[1], sys.argv[2]
lines = open(changelog, encoding="utf-8").read().splitlines()
start = next((i for i, line in enumerate(lines) if re.match(rf"^## {re.escape(version)}(\s|$)", line)), None)
if start is None:
    sys.exit(f"{changelog} has no '## {version}' section; rename the '## Unreleased' section to it before tagging")
end = next((i for i in range(start + 1, len(lines)) if re.match(r"^## \d", lines[i])), len(lines))
section = lines[start + 1:end]
if not "\n".join(section).strip():
    sys.exit(f"The '## {version}' section of {changelog} is empty")


def summary(heading):
    """The text of one "### <heading>" summary, removed from the section; exits when it is missing or empty."""
    first = next((i for i, line in enumerate(section) if line.strip() == f"### {heading}"), None)
    if first is None:
        sys.exit(f"The '## {version}' section of {changelog} has no '### {heading}' summary")
    last = next((i for i in range(first + 1, len(section)) if section[i].startswith("### ")), len(section))
    text = "\n".join(section[first + 1:last]).strip()
    if not re.search(r"^- ", text, re.M):
        sys.exit(f"The '### {heading}' summary of '## {version}' has no bullets")
    del section[first:last]
    return text


highlights = summary("Highlights")
highlights_ko = summary("주요 변경")
body = "\n\n".join(
    [
        "## Highlights\n\n" + highlights,
        "## 주요 변경\n\n" + highlights_ko,
        "## Full changes\n\n" + "\n".join(section).strip(),
    ]
)
# Repository-relative links do not work on a release page; point them at the tagged files on GitHub.
server, repository = os.environ.get("GITHUB_SERVER_URL"), os.environ.get("GITHUB_REPOSITORY")
release_repository = os.environ.get("POLY_RELEASE_REPO") or repository
if release_repository != repository:
    # Released from a private source repository: its files cannot be linked, so keep only the link text.
    body = re.sub(r"\[([^\]]+)\]\((?!https?:|#|mailto:)[^)]+\)", r"\1", body)
elif server and repository:
    ref = os.environ.get("GITHUB_REF_NAME", "main")
    body = re.sub(
        r"\]\((?!https?:|#|mailto:)([^)]+)\)", lambda m: f"]({server}/{repository}/blob/{ref}/{m.group(1)})", body
    )
# The website's download page reads the "Minecraft:" line to group releases by game version.
minecraft = os.environ.get("POLY_MINECRAFT_VERSIONS", "").strip()
if minecraft:
    print("Minecraft: %s" % minecraft)
    print()
print(body)
print()
print("Install: on Windows run `PolyModLoader-%s.exe`; elsewhere run `PolyModLoader-%s.jar` (Java 17 or newer)." % (version, version))
print("설치: Windows는 `PolyModLoader-%s.exe`, 그 밖에는 `PolyModLoader-%s.jar`(Java 17 이상)를 실행합니다." % (version, version))
print("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.")
