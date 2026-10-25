# ADR-0012: 프로젝트 이름을 PolyLoader로 바꾼다

- 상태: 채택 (2026-10-21)
- 관련: ADR-0006(시스템 교체), ADR-0008(모드 SDK), ADR-0011(여러 Minecraft 버전), 설계서 §9(이름 공간)

## 배경
프로젝트 이름은 Void Mod Loader(약칭 VML)였습니다. 이 이름은 문서뿐 아니라 Java 패키지, Gradle 모듈과 플러그인 ID, 메타데이터 파일 이름, 오류 코드, 런타임 모드 ID에도 들어가 있었습니다. 아직 공개 저장소에 발행한 적이 없고 외부 모드도 없으므로, 식별자를 바꾸는 비용은 지금이 가장 작습니다.

## 결정
이름을 **PolyLoader**로 바꾸고, 표시 이름만이 아니라 모든 식별자를 함께 바꿉니다. 짧은 접두사는 `poly`입니다.

| 항목 | 이전 | 이후 |
|---|---|---|
| 표시 이름 | Void Mod Loader (VML) | PolyLoader |
| Java 패키지 | `dev.voidmodloader.*` | `dev.polyloader.*` |
| Maven 그룹 | `dev.voidmodloader` | `dev.polyloader` |
| Gradle 모듈 | `vml-*` | `poly-*` |
| 규약 플러그인 | `vml.java-conventions`, `vml.minecraft-*` | `poly.java-conventions`, `poly.minecraft-*` |
| 모드 개발 플러그인 | `dev.voidmodloader.mod`, `vml-gradle-plugin` | `dev.polyloader.mod`, `poly-gradle-plugin` |
| Gradle 속성 | `-PvmlMinecraft`, `-PvmlTests` 등 | `-PpolyMinecraft`, `-PpolyTests` 등 |
| 메타데이터 파일 | `void.mod.json` | `poly.mod.json` |
| 메타데이터 검증 작업 | `validateVoidModJson` | `validatePolyModJson` |
| 오류 코드 접두사 | `VML-` | `POLY-` |
| 런타임 모드 ID·네임스페이스 | `vml`, `vml:game_brand` | `poly`, `poly:game_brand` |
| 진단 보고서 폴더 | `.vml/` | `.poly/` |
| 클래스 이름 | `Vml*` (`VmlRuntime` 등) | `Poly*` (`PolyRuntime` 등) |
| Minecraft 다운로드 캐시 | `~/.gradle/caches/vml-minecraft` | `~/.gradle/caches/poly-minecraft` |
| 프로젝트 Skill | `.claude/skills/vml-*` | `.claude/skills/poly-*` |

오류 코드는 접두사만 바뀝니다. 번호와 의미는 그대로입니다(`VML-METADATA-012`는 `POLY-METADATA-012`).

## 결과
- 이전 이름을 읽는 호환 계층은 두지 않습니다. `void.mod.json`만 있는 jar는 PolyLoader 모드로 인식되지 않습니다. 발행된 외부 모드가 없으므로 이 결정의 비용은 없습니다.
- 이전 이름으로 만든 월드와 개발 실행 폴더(`run/`)에는 `vml` 네임스페이스 데이터가 남을 수 있습니다. 개발용 데이터이므로 옮기지 않습니다.
- Maven Local에 발행한 이전 좌표(`dev.voidmodloader`)는 쓰지 않습니다. 모드 템플릿을 쓰려면 `publishToMavenLocal`을 다시 실행해야 합니다.
- Git 기록과 커밋 메시지의 이전 이름은 그대로 둡니다.
