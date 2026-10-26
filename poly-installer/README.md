# poly-installer — Cross-Platform Installer & Launcher Integrator

`poly-installer`는 마인크래프트 공식 런처(`.minecraft`) 및 독립 서버 환경에 Poly Mod Loader를 설치·관리할 수 있는 크로스 플랫폼 설치 프로그램 모듈입니다.

---

## 1. 배포 형태 및 아키텍처

Poly 인스톨러는 사용자의 시스템 환경에 맞추어 두 가지 형태로 빌드 및 배포됩니다:

| 배포 파일명 | 대상 플랫폼 | 특징 |
| :--- | :--- | :--- |
| **`PolyModLoader-<version>.jar`** | macOS, Linux, Windows (Java 17+) | Java Swing GUI 기반 크로스 플랫폼 인스톨러. CLI 헤드리스 설치 지원. |
| **`PolyModLoader-<version>.exe`** | Windows 전용 (Java 미설치 환경 대응) | Rust 기반의 경량 네이티브 런처 래퍼. 런처에 내장된 Java를 감지하여 실행. |

---

## 2. 주요 기능

1. **공식 마인크래프트 런처 원클릭 프로필 생성:**
   - `.minecraft/versions` 및 `launcher_profiles.json`에 `Poly Mod Loader <버전>` 프로필을 안전하게 추가합니다.
   - 기존 설정 손상을 방지하기 위해 원자적 파일 쓰기(Atomic Write) 및 백업(`.polymodloader-backup`)을 수행합니다.
2. **다국어 GUI 지원 (영어 / 한국어):**
   - 시스템 언어를 자동 감지하며, 창 우측 상단의 언어 메뉴를 통해 실시간으로 전환할 수 있습니다.
3. **독립 서버 원클릭 설치 (`serverInstall`):**
   - 전용 서버(Dedicated Server) 실행에 필요한 라이브러리, 구동 스크립트, 모드 폴더(`mods/`)를 지정된 디렉터리에 자동으로 구성합니다.
4. **최적 메모리 할당 자동 계산:**
   - 시스템 물리 메모리 용량을 감지하여 추천 JVM 힙 메모리(`-Xmx`)를 프로필에 자동 설정합니다.

---

## 3. 빌드 및 테스트 (Gradle Tasks)

```powershell
# 단위 테스트 실행
.\gradlew.bat :poly-installer:check

# 배포용 jar 및 native exe 일괄 빌드 (poly-installer/build/distributions/)
# * .exe 빌드는 로컬에 Rust(cargo)가 설치되어 있어야 합니다.
.\gradlew.bat :poly-installer:installerDistributions

# 모의 런처 프로필 생성 및 구동 통합 테스트
.\gradlew.bat :poly-bootstrap:launcherProfileTest

# 헤드리스 서버 설치 및 기동 테스트
.\gradlew.bat :poly-installer:serverInstallTest
```

---

## 4. 모듈 구조

- **`dev.polymodloader.installer`**: Java Swing GUI 창, CLI 파서, 프로필 JSON 생성기, 라이브러리 검증기.
- **`native-windows/`**: Rust로 작성된 초경량 Windows 네이티브 실행기 (외부 의존 크레이트 없이 Windows API 직접 호출).
- **`src/main/resources`**: 설치 프로그램 아이콘(`logo.svg`, `icon.png`) 및 다국어 리소스 번들.
