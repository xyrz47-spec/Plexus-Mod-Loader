# build-logic — Gradle Conventions & Minecraft Tooling Plugins

`build-logic`는 Poly Mod Loader 전체 프로젝트의 빌드 규칙, 코드 스타일 검증, 마인크래프트 에셋 프로비저닝, 그리고 모드 개발용 Gradle 플러그인을 제공하는 내부 빌드 로직 모듈(Included Build)입니다.

---

## 1. 제공 플러그인 목록

| 플러그인 ID | 적용 대상 | 기능 |
| :--- | :--- | :--- |
| **`poly.java-conventions`** | 전체 하위 모듈 | Java 25 툴체인 설정, UTF-8 인코딩, Spotless 포맷팅 규칙 일괄 적용 |
| **`poly.minecraft-dev`** | `poly-game-*`, `poly-bootstrap` | Mojang 서버로부터 마인크래프트 클라이언트/서버 jar 및 라이브러리 다운로드, SHA-1 무결성 검증, 런타임 클래스패스 생성 |
| **`poly.mod-template`** | 루트 프로젝트 | 신규 Native 모드 프로젝트 템플릿 생성(`newPolyMod`) 및 배포 패키징 |
| **`poly.sdk-repository`** | 루트 프로젝트 | Poly SDK 아티팩트 빌드 및 로컬 Maven 배포(`publishSdk`) |

---

## 2. 주요 기능 및 파이프라인

1. **마인크래프트 자동 프로비저닝 (`poly.minecraft-dev`):**
   - Mojang 공식 버전 매니페스트를 조회하여 게임 클라이언트, 서버 번들러, 런처 에셋, 외부 의존성 라이브러리를 자동 다운로드합니다.
   - 모든 파일은 공식 SHA-1 해시로 검증되며 `~/.gradle/caches/poly-minecraft`에 캐싱되어 재사용됩니다.
2. **코드 스타일 자동화 (`Spotless`):**
   - Google Java Format 기반의 정밀 포맷터가 통합되어 있습니다.
   - `.\gradlew spotlessApply` 명령으로 프로젝트 전체 코드를 일관된 스타일로 유지합니다.
3. **모드 스캐폴딩 도구:**
   - `ModProjectScaffolder`: 신규 Poly 모드 뼈대 코드 생성.
   - `ModResourceGenerator`: `poly.mod.json` 메타데이터 및 리소스 팩 스펙 검증.

---

## 3. 빌드 및 테스트

```powershell
# build-logic 자체 단위 테스트 실행
.\gradlew.bat -p build-logic check
```
