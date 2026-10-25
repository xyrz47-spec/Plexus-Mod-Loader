# ADR-0023: 이름을 Poly, 모드 로더를 Poly Mod Loader로 바꾼다

- 상태: 채택 (2026-10-23)
- 관련: ADR-0012(PolyLoader로 이름 변경), ADR-0008(모드 SDK), ADR-0017(런처 프로필 설치)

## 배경
ADR-0012에서 프로젝트 이름을 PolyLoader로 정했습니다. 사용자(프로젝트 소유자)가 제품 이름을 **Poly**로 하고, 모드 로더를 가리킬 때는 **Poly Mod Loader**(한국어 **Poly 모드 로더**)로 부르기로 했습니다. ADR-0012 때처럼 표시 이름만이 아니라 식별자도 함께 바꿉니다.

## 결정

| 항목 | 이전 | 이후 |
|---|---|---|
| 제품 이름 | PolyLoader | Poly (웹사이트 상단 이름) |
| 모드 로더 표시 이름 | PolyLoader | Poly Mod Loader / Poly 모드 로더 |
| 코드 식별자 속 이름 | `PolyLoader…` | `PolyModLoader…` |
| Java 패키지 | `dev.polyloader.*` | `dev.polymodloader.*` |
| Maven 그룹 | `dev.polyloader` | `dev.polymodloader` |
| 모드 개발 플러그인 ID | `dev.polyloader.mod` | `dev.polymodloader.mod` |
| 설치 파일 | `PolyLoader-<버전>.jar`/`.exe` | `PolyModLoader-<버전>.jar`/`.exe` |
| 서버 실행 jar | `polyloader-server.jar` | `polymodloader-server.jar` |
| 런처 버전 ID·프로필 | `polyloader-…`, "PolyLoader 26.2" | `polymodloader-…`, "Poly Mod Loader 26.2" |
| Forge 게임 테스트 실행 핸들러 | `polyloader_server_gametest` | `polymodloader_server_gametest` |
| Gradle 루트 프로젝트 | `polyloader` | `polymodloader` |
| 공개 GitHub 저장소 (소유자가 바꿈) | `xyrz47-spec/PolyLoader` | `xyrz47-spec/Poly-Mod-Loader` |

바꾸지 않는 것:

- `poly` 접두사: Gradle 모듈 `poly-*`, 규약 플러그인 `poly.*`, `poly.mod.json`, 오류 코드 `POLY-*`(번호와 의미 포함), 런타임 모드 ID `poly`, `.poly/` 폴더, `-Ppoly*`·`-Dpoly.*` 속성. 이미 짧은 이름 Poly와 맞습니다.
- 웹사이트 도메인 `polyloader.dothome.co.kr`. 바꾸려면 호스팅 설정이 필요하므로 이 결정 밖입니다. 2026-10-23에 소유자가 `www.polymc.net`으로 옮겼습니다(같은 호스팅, FTP 호스트는 그대로).
- 라이선스 이름 "PolyLoader Non-Commercial License 1.0"과 `LICENSE`·`THIRD_PARTY_NOTICES.md` 본문(AGENTS.md: 바꾸지 않음).
- ADR-0012 본문과 Git 기록.

## 결과
- `dev.polyloader`로 발행한 SDK(웹사이트 `/maven/`)는 그대로 남지만 새 버전은 `dev.polymodloader`로만 발행합니다. 그 SDK로 만든 모드는 패키지 이름이 바뀌었으므로 새 SDK로 다시 빌드해야 합니다. 알파 단계라 호환 계층은 두지 않습니다(ADR-0012와 같음).
- 이전 설치 파일로 만든 런처 버전·프로필(`polyloader-…`)은 새 설치가 지우지 않고 옆에 새 것을 만듭니다. 이전 것은 이전 설치 파일의 `uninstall`이나 런처에서 지웁니다.
- 이전 릴리스의 파일 이름(`PolyLoader-<버전>.*`)은 GitHub에 그대로 있으므로, 웹사이트의 다운로드 목록은 두 접두사를 모두 읽습니다.
