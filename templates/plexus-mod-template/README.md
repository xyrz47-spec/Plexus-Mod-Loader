# Poly 모드 로더 모드 템플릿

Poly 모드를 새로 시작하기 위한 독립 Gradle 프로젝트입니다.

## 새 모드 만들기

모드 ID, 이름, Java 패키지, 엔트리포인트 클래스, 프로젝트 이름을 한 번에 바꾸는 방법이 두 가지입니다.

**Poly 모드 로더 저장소 없이:** `https://www.polymc.net/maven/`에서 `poly-mod-template-<버전>.zip`을 다운로드해 압축을 풀고, 그 폴더에서 실행합니다.

```powershell
.\gradlew.bat initPolyMod -PmodId=ruby_mod "-PmodName=Ruby Mod" "-PmodPackage=io.github.you.ruby"
```

**Poly 모드 로더 저장소에서:** 저장소 폴더에서 실행하면 저장소 옆(또는 `-PmodDir=<폴더>`)에 새 프로젝트가 생깁니다.

```powershell
.\gradlew.bat newPolyMod -PmodId=ruby_mod "-PmodName=Ruby Mod" "-PmodPackage=io.github.you.ruby"
```

`-PmodName`을 빼면 모드 ID에서 만들고, `-PmodPackage`를 빼면 `com.example.<모드 ID>`입니다. 폴더를 직접 복사해도 됩니다. 그때는 아래 "바꿀 곳"을 손으로 고칩니다.

## Poly 모드 로더를 다운로드하는 곳

`settings.gradle.kts`와 `dev.polymodloader.mod` 플러그인은 Poly 모드 로더를 다음 순서로 찾습니다. 따로 준비할 것은 없습니다.

1. 웹사이트의 공개 Maven 저장소 `https://www.polymc.net/maven/` (`dev.polymodloader` 그룹만)
2. Maven Local: Poly 모드 로더 저장소에서 `.\gradlew.bat publishToMavenLocal`과 `.\gradlew.bat -p build-logic publishToMavenLocal`로 올린 로컬 빌드
3. Gradle Plugin Portal, Maven Central(그 밖의 라이브러리)

Gradle은 처음 찾은 곳의 것을 씁니다. 이미 공개된 버전을 로컬 빌드로 시험하려면 명령에 `-PpolyLocalSdk`를 붙여 Maven Local을 먼저 보게 합니다. 공개 저장소에 연결할 수 없으면 빌드가 실패하므로, 오프라인에서는 `--offline`을 씁니다.

## 사용

```powershell
.\gradlew.bat build                  # 컴파일 + poly.mod.json 검증 + 리소스 생성·검사
.\gradlew.bat worldCreateSaveTest    # 게임 테스트 서버에서 이 모드를 로드하고 월드 생성·저장 (헤드리스)
.\gradlew.bat gameTests "-PpolyTests=my_mod:*"   # 이 모드의 게임 내 테스트 실행 (헤드리스)
.\gradlew.bat runClient              # 클라이언트 실행 (run/client)
```

IntelliJ IDEA에서는 폴더를 Gradle 프로젝트로 열고, `.run/runClient` 실행 구성을 실행하거나 디버그합니다.

## 리소스 파일

`build.gradle.kts`의 `polyResources`에 적은 블록·아이템은 아이템 정의, 모델, 블록 상태, 전리품, 영어 이름, 도구 태그를 빌드 때 만듭니다(`build/generated/poly-resources/main`). 그래서 이 템플릿의 `ruby`와 `ruby_block`에는 JSON 파일이 없습니다. `src/main/resources`에 같은 경로의 파일을 직접 쓰면 그 파일이 쓰입니다.

`build`는 `checkPolyResources`로 대문자 파일 이름, 깨진 JSON, 없는 모델·텍스처, 빠뜨린 네임스페이스를 찾아 알려 줍니다. 결과는 `build/reports/poly/resource-problems.txt`에 있습니다.

## 바꿀 곳

| 파일 | 내용 |
|---|---|
| `src/main/resources/poly.mod.json` | 모드 ID, 이름, 버전, 엔트리포인트, 의존성 |
| `src/main/java/com/example/mymod/MyMod.java` | 엔트리포인트(`CommonModInitializer`). 아이템 1개, 블록 1개, 게임 테스트 2개 예시 |
| `build.gradle.kts` | `polyResources`(생성할 리소스), `worldCreateSaveTest`에서 확인할 로그 줄 |
| `src/main/resources/assets/my_mod/` | 텍스처와 직접 쓰는 모델·번역. 데이터(레시피·전리품·태그)는 `data/my_mod/`에 둡니다 |
| `settings.gradle.kts` | 프로젝트 이름 |
| `gradle.properties` | `polyMinecraft=<버전>`: 실행·테스트에 쓸 Minecraft 버전(없으면 Poly 모드 로더 기본 버전) |

`poly.mod.json`의 `"minecraft"`는 모드가 도는 버전입니다. Native API만 쓰면 `26.*`나 `>=26.2 <27`처럼 적어 새 버전에서도 다시 빌드하지 않고 쓸 수 있습니다. 특정 버전의 게임 코드에 기대는 모드는 `26.2`처럼 정확히 적습니다.

사용할 수 있는 API는 웹사이트의 API 레퍼런스(`https://www.polymc.net/docs/api-reference/`)에 있습니다(`docs/API_CATALOG.md`는 공개 저장소에 없습니다). IDE는 공개 API 모듈의 JavaDoc jar를 Maven 저장소에서 함께 다운로드합니다. 모든 API는 아직 `PREVIEW`입니다.
