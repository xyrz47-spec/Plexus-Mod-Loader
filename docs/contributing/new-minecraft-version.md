# 새 Minecraft 버전 지원하기

Poly 모드 로더는 게임 버전마다 **모듈 두 개**만 따로 둡니다. 모드가 쓰는 Native API, 로더, 변환 엔진, 빌드 플러그인은 모든 버전이 함께 씁니다(ADR-0011).

| 모듈 | 내용 |
|---|---|
| `poly-version-adapter-v<id>` | 그 버전의 실행 대상(메인 클래스)과 변환 규칙. 게임 클래스·메서드 이름은 여기와 게임 계층에만 있습니다 |
| `poly-game-v<id>` | 그 버전의 게임 클래스로 Native API를 구현한 게임 계층 |

`<id>`는 버전의 `.`과 `-`를 `_`로 바꾼 값입니다(26.2 → `26_2`). 패키지와 클래스 이름도 같은 규칙을 따릅니다. 게임 계층은 `dev.polymodloader.game.layer.v26_2.GameLayer26_2`이고, 어댑터는 `dev.polymodloader.game.version.v26_2.Minecraft26_2Adapter`입니다. 빌드는 이 규칙으로 모듈과 클래스를 찾습니다.

## 지원 버전 목록

지원 버전은 `gradle/minecraft-versions.properties` 한 곳에 있습니다.

```properties title="gradle/minecraft-versions.properties"
# 지원 버전, 오래된 순
supported=26.2
# 예제, runClient/runServer, 검증 작업이 쓰는 버전
default=26.2
# 버전별 Java 기능 버전; 그 버전의 모듈은 이 Java로 컴파일됩니다
java.26.2=25
```

- `settings.gradle.kts`는 목록의 버전마다 두 모듈을 포함합니다. 디렉터리가 없으면 만드는 명령을 알려 주고 실패합니다.
- 버전 모듈은 이름에 든 버전의 클라이언트 jar에 컴파일됩니다.
- `poly-bootstrap`은 모든 버전의 어댑터를 담습니다. 실행할 때 게임 jar의 `version.json`을 읽고 그 버전을 맡는 어댑터를 고릅니다.
- `./gradlew minecraftVersions`로 지원 버전과 모듈을 봅니다.

## 새 버전 추가 절차

### 0. (선택) 게임 파일을 직접 넣기

빌드는 게임 파일을 Mojang에서 받습니다. 받는 대신 직접 넣으려면 런처의 `.minecraft/versions/<버전>/`을 저장소의
`MinecraftFiles/<버전>/`에 그대로 복사합니다(`<버전>.json`, `<버전>.jar`, 선택으로 서버 번들러 `server.jar`와 Maven 구조의
`libraries/`). 빌드는 jar마다 `<버전>.json`의 SHA-1을 확인한 뒤 다운로드 캐시로 복사합니다. 해시가 다르면 파일과 두 해시를
알리고 실패하며, 없는 파일과 에셋은 지금처럼 내려받습니다. 다른 폴더는 `-PpolyMinecraftFiles=<경로>`로 지정합니다.
`MinecraftFiles/`는 README만 커밋되고 나머지는 git이 무시합니다. Minecraft 파일은 커밋하지 않습니다. 자세한 내용은
[`MinecraftFiles/README.md`](../../MinecraftFiles/README.md)에 있습니다.

26.1보다 오래된 버전은 난독화되어 있어 매핑 계층이 필요한데, 아직 없습니다([아래](#261-이전난독화된-버전) 참고). 1.13 이전
형식(`minecraftArguments`)의 버전 JSON처럼 설치기가 다루지 못하는 형식은 분명한 메시지로 거부합니다.

### 0.5. 무엇이 바뀌었는지 보기

```bash
./gradlew minecraftVersionDiff -Pfrom=26.2 -Pto=26.3
```

두 클라이언트 jar만 받아, 지원 버전의 어댑터와 게임 계층이 쓰는 게임 클래스·필드·메서드 중 새 버전에서 없어지거나
바뀐 것을 `build/reports/minecraft-version-diff/<from>-to-<to>.md`에 씁니다. 버전 JSON의 Java 버전, 라이브러리, JVM 인수
차이도 함께 적습니다. `-Pto`는 스냅숏을 포함해 Mojang 버전 목록의 어떤 ID든 됩니다. `javac`가 인라인한 상수와 변환 규칙의
멤버 대상은 보지 못하므로, 아래 2단계의 컴파일과 `BuiltInRulesTest`가 여전히 필요합니다. PowerShell에서는
`"-Pfrom=26.2"`처럼 따옴표로 감쌉니다(따옴표가 없으면 `.`에서 잘립니다).

### 1. 가장 가까운 버전 복사

```bash
./gradlew newMinecraftVersion -Pfrom=26.2 -Pto=26.3
# Java 버전이 바뀌었으면: -Pjava=26
```

이 작업이 하는 일:
- `poly-version-adapter-v26_2`와 `poly-game-v26_2`를 `-v26_3`으로 복사합니다. 빌드 출력과 로그는 복사하지 않습니다.
- 경로, 패키지·클래스 이름, 문자열 안의 버전을 바꿉니다.
- 목록에 `26.3`을 더합니다.

버전 토큰은 통째로 일치할 때만 바뀝니다. `26.2.1`이나 `126_2`는 그대로 둡니다. 기본 버전(`default`)은 바꾸지 않습니다.
작업은 마지막에 `MinecraftFiles/<새 버전>/`에서 찾은 파일도 알려 줍니다. Java 버전은 `<버전>.json`의
`javaVersion.majorVersion`에서 확인할 수 있습니다.

### 2. 새 게임에 맞게 고치기

```bash
./gradlew :poly-game-v26_3:compileJava
```

- **컴파일 오류:** 게임에서 이름이나 매개변수가 바뀐 곳입니다. 게임 계층만 고칩니다. 공개 API(`poly-game-api`)는 바꾸지 않습니다.
- **변환 규칙:** 새 어댑터의 규칙마다 대상(owner, method, descriptor)이 새 jar에 있는지 `javap`로 확인합니다. 대상을 찾지 못한 규칙은 실행할 때 `.poly/transformation-report.json`에 이름으로 남습니다.
- **버전 데이터:** 데이터 팩·리소스 팩 형식 번호처럼 게임 버전마다 다른 값을 고칩니다.
- **반사로 쓰는 멤버:** 게임 계층은 일부 private 멤버를 이름으로 찾습니다(`getDeclaredField`/`getDeclaredMethod`). 컴파일러가 잡지 못하므로 새 jar에서 하나씩 확인합니다.
- **훅 시그니처:** 규칙이 부르는 훅의 매개변수는 대상 메서드에서 정해집니다. 대상을 바꾸면 `GameHooks`/`ClientHooks`의 훅도 같이 바꿉니다. 맞지 않으면 실행 중에 `NoSuchMethodError`가 납니다. 클라이언트 전용 규칙은 창을 열기 전에는 실행되지 않으므로, 변환 계획(`ClassTransformPlanner`)과 적용(`ClassTransformExecutor`)을 클라이언트 jar에 직접 돌리고 훅이 있는지 보는 것이 빠릅니다.
- `newMinecraftVersion`은 새 버전을 `supported` 끝에 붙입니다. 더 오래된 버전을 더했으면 목록을 오래된 순으로 고칩니다. 설치기는 마지막 버전을 기본으로 고릅니다.

### 3. 새 버전으로 검증

실행·검증 작업은 한 번에 한 버전입니다. `-PpolyMinecraft`로 고릅니다.

```bash
./gradlew check
./gradlew :poly-bootstrap:serverBootSmokeTest :poly-bootstrap:worldCreateSaveTest \
          :poly-bootstrap:nativeApiGameTests :poly-bootstrap:compatibilityMatrix -PpolyMinecraft=26.3
./gradlew :poly-bootstrap:clientWorldReloadTest -PpolyMinecraft=26.3   # 게임 창이 열립니다
```

테스트 모드와 `transform-demo-mod`는 메타데이터에 26.2를 적었지만, 저장소 빌드는 그것을 고른 버전으로 바꿔 빌드합니다(루트 `build.gradle.kts`). 그래서 같은 기대 로그 줄로 모든 버전을 검증합니다. 그 모드들이 쓰는 게임 멤버(`Mth.SIN`, `SharedConstants.prefixDebugFlagName`, `LevelStorageSource.getName`, `MinecraftServer.tickServer`/`tickCount`, `Util.getNanos`)는 새 버전에서도 확인합니다.

`compatibilityCorpus`는 `poly-bootstrap/compat/mod-corpus-<버전>.json`이 있는 버전에서만 돌립니다. 다른 작업은 이 파일이 없어도 됩니다.

`nativeApiGameTests`는 예제 모드의 게임 내 테스트를 모두 돌립니다. 이 테스트가 통과하면 새 버전의 게임 계층이 Native API를 이전 버전과 같게 구현했다는 뜻입니다.

### 4. 기본 버전으로 만들기

준비가 끝나면 `default`를 새 버전으로 바꿉니다. 모드 개발 플러그인은 이 목록을 함께 배포합니다. 모드 프로젝트는 `gradle.properties`의 `polyMinecraft=<버전>`으로 실행할 버전을 고르고, 지정하지 않으면 기본 버전을 씁니다.

### 5. 문서 버전 추가

이 사이트의 현재 문서를 그 버전 이름으로 고정하고, 현재 문서의 버전 값을 새 버전으로 바꿉니다. 웹사이트 소스는 공개 저장소에 들어 있지 않아 프로젝트 관리자가 합니다.

## 26.1 이전(난독화된) 버전

1.21.11 이하 버전은 클래스·멤버 이름이 난독화되어 있습니다. 그래서 위 절차를 그대로 쓸 수 없습니다. 계획은
[ADR-0019](../adr/0019-obfuscated-minecraft-versions.md)(제안 상태, 일부만 구현)에 있습니다.

- 1.14.4~1.21.11은 Mojang 공식 매핑으로 게임 jar를 Mojang 이름으로 다시 매핑한 뒤 그 jar로 컴파일·실행합니다. 매핑과 다시 매핑한
  jar는 내 PC의 캐시에만 두고, 커밋하거나 배포하지 않습니다.
- 1.8.9·1.12.2는 공식 매핑이 없어 범위 밖입니다.
- 모든 버전을 Java 25로 실행합니다. 그래서 이 버전들에는 `<버전>.json`의 `javaVersion.majorVersion`(8·16·17·21)이 아니라
  `-Pjava=25`를 씁니다.
- 이 버전들은 **실험 버전**으로만 더합니다: `newMinecraftVersion -Pfrom=26.1 -Pto=<버전> -Pjava=25 -Pexperimental=true`.
  `gradle/minecraft-versions.properties`의 `experimental`에 들어가 모듈은 빌드·테스트되고 `-PpolyMinecraft`로 실행할 수
  있지만, 설치 프로그램·SDK·웹사이트에는 나오지 않습니다. 지원으로 옮기는 것은 ADR-0019 8번의 검사를 통과한 뒤입니다.
  1.21.11이 첫 실험 버전입니다(서버 PoC 통과, 2026-10-24).
- 설치기는 공식 매핑이 있는 버전이면 Mojang 이름으로 다시 매핑한 jar를 컴파일과 실행의 게임 jar로 씁니다.
  `remapMinecraft -Pminecraft=<버전> [-Pside=server]`는 같은 jar를 만들고 자체 검사(남은 난독화 이름, 중복 멤버,
  선언한 곳을 찾지 못한 참조, Java 25에서 모든 클래스 로드·링크)를 합니다. 결과 jar는 Gradle 캐시에만 둡니다.
- 1.x 데이터 팩 형식은 26.x와 다릅니다. 26.x용 데이터 파일을 든 모드는 1.x에서 데이터를 읽다 실패할 수 있습니다.
- `minecraftVersionDiff -Pfrom=26.1 -Pto=1.21.11`은 이 버전들도 비교합니다. 공식 매핑을 받아(SHA-1 확인, 캐시에만 둠)
  이름을 번역하므로 보고서는 Mojang 이름입니다. 다시 매핑하면 바이트가 달라지므로 바뀐 클래스는 비교하지 않습니다. 결과와
  남은 일은 저장소의 `docs/plans/MINECRAFT_1_21.md`(1단계)와 `docs/plans/MINECRAFT_1_16_5.md`(3단계)에 있습니다.

## 핫픽스 버전

변환 대상과 게임 계층이 쓰는 부분이 바뀌지 않은 핫픽스(예: 26.2.1)는 모듈을 새로 만들지 않습니다. 기존 어댑터의 `gameVersions()`에 더합니다.

```java
@Override
public Set<String> gameVersions() {
    return Set.of("26.2", "26.2.1");
}
```

그리고 `gradle/minecraft-versions.properties`에 그 버전 아래 핫픽스로 적습니다.

```properties
hotfixes.26.1=26.1.1,26.1.2
```

- 핫픽스는 모듈이 없습니다. 적힌 버전의 어댑터와 게임 계층으로 실행합니다.
- `-PpolyMinecraft=26.1.1`로 고를 수 있고, 설치기도 목록에 보여 줍니다. 기대 로그 줄의 어댑터 ID는 모듈 버전(`minecraft-26.1`)입니다.
- 어댑터의 규칙은 `"26.1"`처럼 한 버전을 적습니다. 런타임은 그 규칙을 실행 중인 핫픽스 버전으로 계획합니다(`AdapterTransformRules`). 모드의 규칙은 그대로 정확한 버전만 받습니다.

먼저 `minecraftVersionDiff -Pfrom=<지원 버전> -Pto=<핫픽스>`를 돌립니다. 판정이 "Hotfix candidate"이면 모듈이 쓰는 클래스가 하나도 바뀌지 않은 것이고, 남은 것은 인라인된 상수와 변환 규칙 대상의 확인입니다. 바뀐 클래스가 있으면 아래처럼 멤버를 비교합니다.

바뀌지 않았는지는 두 클라이언트 jar의 클래스를 하나씩 비교해 확인합니다. 바뀐 클래스 가운데 어댑터 규칙의 대상이나 게임 계층이 참조하는 클래스(`javap -v`의 상수 풀)가 있으면 그 멤버를 `javap -p -c`로 비교합니다. 게임 계층이 `static final` 상수를 쓰면 컴파일할 때 값이 박히므로 상수가 바뀐 것도 차이입니다.

하나라도 바뀌었으면 새 버전으로 추가합니다.

## 모드 쪽

모드는 `poly.mod.json`의 `"minecraft"`로 자신이 도는 버전을 밝힙니다.
- **Native API만 쓰는 모드:** `"26.*"`나 `">=26.2 <27"`처럼 적습니다. Poly 모드 로더가 새 버전을 지원하면 다시 빌드하지 않고 그 버전에서 돕니다.
- **특정 버전의 게임 코드에 기대는 모드:** 변환 규칙을 선언하는 모드가 여기에 해당합니다. `"26.2"`처럼 정확한 버전을 적습니다.

자세한 형식은 poly.mod.json(문서 사이트)에 있습니다.

## 게임 계층 코드를 고칠 때

버전마다 게임 계층이 따로 있으므로, 한 버전의 버그를 고치면 다른 지원 버전의 같은 코드에도 반영합니다. 각 버전에서 `nativeApiGameTests`로 확인합니다.
