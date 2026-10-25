# Crystal Garden

웹 문서의 튜토리얼 [첫 Poly 모드 만들기](https://www.polymc.net/ko/docs/getting-started/first-mod/)에서 만드는 Poly 모드입니다. 크리스털 새싹을 심으면 4단계로 자라고, 다 자란 새싹을 쓰면 크리스털을 얻습니다.

이 폴더는 Poly 모드 로더 저장소의 빌드에 포함되지 않는 **독립 Gradle 프로젝트**입니다. 모드 제작자가 쓰는 방식(모드 플러그인 `dev.polymodloader.mod`, 웹사이트의 Maven 저장소) 그대로 빌드합니다. `newPolyMod`로 만든 뒤 튜토리얼대로 코드를 채웠습니다.

## 보여 주는 Native API

| 기능 | 파일 |
|---|---|
| 아이템·블록 선언, `polyResources`로 리소스 자동 생성 | `CrystalGardenMod.java`, `build.gradle.kts` |
| 블록 상태 속성, 상태별 모양·밝기, 매 틱 도는 블록 엔티티, 블록 동작 | `CrystalSprout.java` |
| 엔티티 데이터(소유자 동기화), 월드 저장소(클라이언트 동기화) | `CrystalSprout.harvest` |
| 명령어, 권한 노드 | `GardenCommands.java` |
| 접속 준비 단계 교환(모드 없는 클라이언트 안내) | `CrystalGardenMod.java`, `CrystalGardenClient.java` |
| 블록 엔티티 그리기, HUD 레이어, 키 바인딩 | `CrystalGardenClient.java` |
| 게임 내 테스트 4개 | `GardenTests.java` |

## 빌드와 테스트

Poly 모드 로더는 웹사이트의 Maven 저장소(`https://www.polymc.net/maven/`)에서 받습니다. Poly 모드 로더를 고친 뒤 이 모드로 시험하려면 Poly 모드 로더 저장소에서 로컬에 게시하고, 아래 명령에 `-PpolyLocalSdk`를 붙여 Maven Local을 먼저 보게 합니다.

```powershell
.\gradlew.bat publishToMavenLocal
.\gradlew.bat -p build-logic publishToMavenLocal
```

이 폴더에서:

```powershell
.\gradlew.bat build                                       # 컴파일 + poly.mod.json 검증 + 리소스 생성·검사
.\gradlew.bat gameTests "-PpolyTests=crystal_garden:*"    # 게임 내 테스트 4개 (헤드리스)
.\gradlew.bat worldCreateSaveTest                         # 월드 생성·저장과 로그 확인 (헤드리스)
.\gradlew.bat clientWorldReloadTest                       # 클라이언트로 월드 열기, 접속 확인 통과 확인 (창이 뜸)
.\gradlew.bat runClient                                   # 직접 해 보기
```

## 제한

- 텍스처와 새싹 모델은 바닐라 자수정 것을 빌려 씁니다.
- 성장 표시와 HUD는 `clientWorldReloadTest`로 클라이언트가 오류 없이 접속하는 것까지만 자동 확인합니다. 그림 자체는 `runClient`로 눈으로 확인합니다.
