# 빌드와 테스트

Poly 모드 로더 저장소 루트에서 실행합니다.

## 기본 검증

```powershell
.\gradlew.bat check              # 컴파일, 포맷 검사, 단위·통합 테스트, JavaDoc 검사
.\gradlew.bat spotlessApply      # 포맷 오류 자동 수정
```

- 컴파일 경고는 오류로 취급합니다(`-Xlint:all -Werror`).
- 공개 API의 JavaDoc 오류는 빌드를 실패시킵니다.
- 포매터 변경과 기능 변경은 커밋을 분리합니다.

## 실제 게임 검증

| 작업 | 확인하는 것 | 창 |
|---|---|---|
| `:poly-bootstrap:serverBootSmokeTest` | 실제 Minecraft 26.2 서버를 Poly 모드 로더로 부팅(EULA 확인 단계까지, 네트워크 필요) | 없음 |
| `:poly-bootstrap:worldCreateSaveTest` | Mojang 게임 테스트 서버에서 월드 생성·게임 테스트·저장(EULA 불필요) | 없음 |
| `:poly-bootstrap:nativeApiGameTests` | `native-showcase-mod`의 Native API 게임 테스트 | 없음 |
| `:poly-bootstrap:clientWorldReloadTest` | 저장된 월드를 클라이언트로 다시 열기, 키 바인딩·HUD·네트워크 왕복 | 잠깐 뜸 |
| `:poly-bootstrap:compatibilityMatrix` | 혼합 로더 행렬(Native·Fabric·NeoForge 조합)과 변환 충돌 회귀 | 없음 |
| `:poly-bootstrap:startupBenchmark` | 시작 시간 측정([성능](../PERFORMANCE.md)) | 없음 |
| `:poly-bootstrap:runClient` | 클라이언트 수동 확인 | 뜸 |

```powershell
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest
.\gradlew.bat :poly-bootstrap:nativeApiGameTests
.\gradlew.bat :poly-bootstrap:runClient
```

## 모드 개발용으로 게시

모드 프로젝트에서 바뀐 Poly 모드 로더를 쓰려면 Maven Local에 다시 게시합니다.

```powershell
.\gradlew.bat check publishToMavenLocal
.\gradlew.bat -p build-logic publishToMavenLocal
```

## 완료를 주장하기 전에

- 완료 주장은 실행한 테스트 결과를 근거로 합니다.
- 빌드·테스트를 실행하지 못했으면 그 사실과 검증하지 못한 범위를 적습니다.
- 새 공개 API에는 테스트와 예제(가능하면 `native-showcase-mod`의 게임 테스트)를 함께 추가합니다.

## 문서 사이트

이 문서 사이트는 Docusaurus 프로젝트이며, 소스(`site/`)는 공개 저장소에 들어 있지 않습니다. 문서의 잘못된 내용은 이슈로 알려 주세요.
