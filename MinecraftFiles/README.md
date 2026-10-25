# MinecraftFiles

Put Minecraft files for a version you are adding here, one folder per version. The build copies them into
its download cache (`~/.gradle/caches/poly-minecraft/`) instead of downloading them from Mojang.

Everything in this folder except this README is ignored by git. **Never commit Minecraft files.**

## Layout

```
MinecraftFiles/
  <version>/
    <version>.json      the launcher's version JSON (required for the folder to be used for it)
    <version>.jar       the client jar (client.jar is accepted too)
    server.jar          optional: Mojang's dedicated server jar (the "bundler")
    libraries/          optional: libraries in Maven layout, e.g. org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar
```

The easiest source is the official Minecraft Launcher: start the version once, then copy
`.minecraft/versions/<version>/` here as is. The server jar is the one linked from `downloads.server.url` in
`<version>.json` (Mojang's server download page).

## What the build does with them

- `<version>.json` is used instead of fetching Mojang's version manifest. It must be the vanilla JSON: its `id`
  must equal the folder name, and modded launcher profiles (`inheritsFrom`) are rejected.
- Each jar is checked against the SHA-1 in `<version>.json` (`downloads.client.sha1`, `downloads.server.sha1`,
  and each library's `sha1`) and then copied into the cache. **A file whose hash differs fails the build**, naming
  the file and both hashes. Copy it again from the same version folder, or delete it to download it instead.
- A file that is missing is downloaded from Mojang as usual. Libraries not in `libraries/` and all assets are
  still downloaded, so the first build of a version needs the network.
- Only files that are not already in the cache are copied. Tasks that are up to date do not look here.

Another folder can be used with `-PpolyMinecraftFiles=<path>` (or `polyMinecraftFiles=<path>` in
`gradle.properties`; relative paths start at the root project). Mod projects built with the `dev.polymodloader.mod`
plugin use `<mod project>/MinecraftFiles` the same way, or the property.

## Next steps

Adding files here does not add a version. Then follow
[`docs/contributing/new-minecraft-version.md`](../docs/contributing/new-minecraft-version.md):

1. `.\gradlew.bat newMinecraftVersion -Pfrom=26.2 -Pto=<version>` (add `-Pjava=<n>` if `javaVersion.majorVersion`
   in `<version>.json` differs). The task also prints which local files it found for `<version>`.
2. `.\gradlew.bat :poly-game-v<id>:compileJava` and fix what changed in the game layer.
3. Check every transformation rule of the new adapter against the new jar with `javap`.
4. Update version data (data and resource pack formats).
5. Verify with `-PpolyMinecraft=<version>`: `check`, `serverBootSmokeTest`, `worldCreateSaveTest`,
   `nativeApiGameTests`, `compatibilityMatrix`.

## Limits

- **Versions before 26.1 are obfuscated.** Poly Mod Loader compiles and runs against the game's real class names, so
  those versions need a mapping layer, which does not exist yet. They can be downloaded but not supported. The
  plan is [ADR-0019](../docs/adr/0019-obfuscated-minecraft-versions.md) (proposed). 1.14.4–1.21.11 would be
  remapped to Mojang names with Mojang's official mappings, on your machine only. 1.8.9 and 1.12.2 have no official
  mappings and are out of scope. Old version folders here are used only to read facts from their files for now.
  Never commit them, and never commit mapping files (`client.txt`, `server.txt`) or remapped jars.
- Version JSONs from before 1.13 (`minecraftArguments`), natives-only library entries (before 1.19), and plain
  server jars that are not bundlers (before 1.18) are rejected with a clear message.

---

## 한국어

추가할 Minecraft 버전의 파일을 버전마다 폴더 하나로 여기에 둡니다. 빌드는 Mojang에서 받는 대신 이 파일을 다운로드
캐시(`~/.gradle/caches/poly-minecraft/`)로 복사합니다.

이 README 말고는 모두 git이 무시합니다. **Minecraft 파일은 절대 커밋하지 않습니다.**

### 구성

```
MinecraftFiles/
  <버전>/
    <버전>.json      런처의 버전 JSON (이 폴더를 쓰려면 필요)
    <버전>.jar       클라이언트 jar (client.jar도 됨)
    server.jar       선택: Mojang 전용 서버 jar(번들러)
    libraries/       선택: Maven 구조의 라이브러리 (예: org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar)
```

가장 쉬운 방법: 공식 Minecraft Launcher로 그 버전을 한 번 실행한 뒤 `.minecraft/versions/<버전>/`을 그대로 복사합니다.
서버 jar는 `<버전>.json`의 `downloads.server.url`(Mojang 서버 다운로드 페이지)에 있는 파일입니다.

### 빌드가 하는 일

- Mojang 버전 매니페스트 대신 `<버전>.json`을 씁니다. 바닐라 JSON이어야 합니다: `id`가 폴더 이름과 같아야 하고,
  모드 로더 런처 프로필(`inheritsFrom`)은 거부합니다.
- jar마다 `<버전>.json`의 SHA-1(`downloads.client.sha1`, `downloads.server.sha1`, 라이브러리의 `sha1`)과 비교한 뒤
  캐시로 복사합니다. **해시가 다르면 빌드가 실패**하고 파일과 두 해시를 알려 줍니다. 같은 버전 폴더에서 다시 복사하거나,
  파일을 지워 내려받게 합니다.
- 없는 파일은 지금처럼 Mojang에서 받습니다. `libraries/`에 없는 라이브러리와 에셋은 모두 내려받으므로, 버전의 첫 빌드에는
  네트워크가 필요합니다.
- 캐시에 이미 있는 파일은 복사하지 않습니다. 최신 상태(up-to-date)인 작업은 이 폴더를 보지 않습니다.

다른 폴더는 `-PpolyMinecraftFiles=<경로>`(또는 `gradle.properties`의 `polyMinecraftFiles=<경로>`, 상대 경로는 루트
프로젝트 기준)로 지정합니다. `dev.polymodloader.mod` 플러그인을 쓰는 모드 프로젝트도 `<모드 프로젝트>/MinecraftFiles`나
이 속성을 같은 방식으로 씁니다.

### 다음 단계

파일을 넣는 것만으로 버전이 추가되지는 않습니다. 이어서
[`docs/contributing/new-minecraft-version.md`](../docs/contributing/new-minecraft-version.md)를 따릅니다.

1. `.\gradlew.bat newMinecraftVersion -Pfrom=26.2 -Pto=<버전>` (`<버전>.json`의 `javaVersion.majorVersion`이 다르면
   `-Pjava=<n>`). 이 작업은 `<버전>`의 로컬 파일을 찾았는지도 출력합니다.
2. `.\gradlew.bat :poly-game-v<id>:compileJava`로 컴파일하고 게임 계층에서 바뀐 곳을 고칩니다.
3. 새 어댑터의 변환 규칙 대상을 새 jar에서 `javap`로 확인합니다.
4. 버전 데이터(데이터 팩·리소스 팩 형식)를 고칩니다.
5. `-PpolyMinecraft=<버전>`으로 검증합니다: `check`, `serverBootSmokeTest`, `worldCreateSaveTest`,
   `nativeApiGameTests`, `compatibilityMatrix`.

### 제한

- **26.1보다 오래된 버전은 난독화되어 있습니다.** Poly 모드 로더는 게임의 실제 클래스 이름으로 컴파일·실행하므로 이런
  버전에는 매핑 계층이 필요한데, 아직 없습니다. 내려받을 수는 있어도 지원할 수는 없습니다. 계획은
  [ADR-0019](../docs/adr/0019-obfuscated-minecraft-versions.md)(제안 상태)에 있습니다. 1.14.4~1.21.11은 Mojang 공식 매핑으로
  내 PC에서만 Mojang 이름으로 다시 매핑합니다. 1.8.9·1.12.2는 공식 매핑이 없어 범위 밖입니다. 지금 여기 둔 이전 버전 폴더는
  파일에서 사실을 확인하는 데에만 씁니다. 그 폴더도, 매핑 파일(`client.txt`, `server.txt`)도, 다시 매핑한 jar도 커밋하지 않습니다.
- 1.13 이전 버전 JSON(`minecraftArguments`), natives 전용 라이브러리 항목(1.19 이전), 번들러가 아닌 서버 jar(1.18 이전)는
  분명한 메시지와 함께 거부합니다.
