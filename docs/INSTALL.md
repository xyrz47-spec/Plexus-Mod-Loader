# 설치·업데이트·오류 복구 (0.1.0-alpha.10)

## 플레이어: 설치 프로그램

1. Minecraft Launcher에서 바닐라 26.2를 한 번 실행했다가 끄고, 런처를 닫습니다.
2. `PolyModLoader-<버전>.exe`(Windows) 또는 `PolyModLoader-<버전>.jar`(Java 17 이상)를 실행해 **설치**를 누릅니다. 명령줄: `java -jar PolyModLoader-<버전>.jar install [--dir <런처 폴더>]`
3. 런처에서 **Poly 모드 로더 26.2** 프로필로 플레이합니다. 모드는 런처 폴더의 `mods/`에 넣습니다.
4. 제거: 설치 프로그램의 **제거** 또는 `uninstall`. `mods/`와 월드는 남습니다.

설치 프로그램이 쓰는 파일과 검증 범위는 `poly-installer/README.md`와 ADR-0017에 있습니다.

## 개발자: 소스에서 실행

1. JDK 25를 설치합니다.
2. 저장소를 복제한 뒤 `.\gradlew.bat check`를 실행합니다. 처음 실행할 때 Mojang에서 Minecraft 26.2를 받아 해시로 검증합니다.
3. 게임 실행: `.\gradlew.bat :poly-bootstrap:runClient`
4. 모드 개발: `docs/DEVELOPMENT_IN_INTELLIJ.md`의 "새 모드 프로젝트 만들기"를 따릅니다.
5. 설치 파일 만들기: `.\gradlew.bat :poly-installer:installerDistributions`(결과: `poly-installer/build/distributions/`)

## 모드 넣기
플레이어는 런처 폴더의 `mods/`, 개발 실행은 `run/client/mods/`(서버는 `run/server/mods/`)에 jar를 넣습니다.
- Poly 모드(`poly.mod.json`)는 로드합니다.
- Fabric 모드는 지원되는 기능만 쓰는 경우 `Experimental`로 로드합니다(`docs/COMPATIBILITY.md`).
- 그 밖의 모드는 이유를 로그에 남기고 로드하지 않습니다.

## 업데이트
1. `git pull`
2. `.\gradlew.bat check publishToMavenLocal`
3. `.\gradlew.bat -p build-logic publishToMavenLocal`(모드 개발을 할 때)
4. 변환 캐시는 Poly 모드 로더·규칙·게임 버전이 바뀌면 자동으로 무효화되므로, 지울 필요가 없습니다. 크기는 256 MiB(`-Dpoly.transformCacheMaxMegabytes`)를 넘지 않도록 오래 쓰지 않은 항목부터 정리됩니다.

## 오류 복구

| 증상 | 확인할 곳 | 조치 |
|---|---|---|
| `Poly 모드 로더 launch stopped in phase …` | 콘솔과 `logs/latest.log`(런처 폴더 또는 `run/<side>/`)의 오류 코드 | `docs/ARCHITECTURE.md`의 오류 코드 표를 보고 해당 모드를 고치거나 제거합니다 |
| 모드가 로드되지 않음 | `run/<side>/.poly/startup-report.json`의 `blockedFiles`·`foreignMods` | 기능별 `POLY-COMPAT-*` 이유를 확인합니다 |
| 변환 관련 이상 | `.poly/transformation-report.json`, `-Dpoly.transformDumpDir=<dir>` | 캐시를 의심하면 `-Dpoly.transformCacheDir=none`으로 실행해 비교합니다. 손상된 캐시 항목은 자동으로 버립니다 |
| `POLY-BOOTSTRAP-006` | 런처 프로필의 클래스 경로 | 설치 프로그램으로 다시 설치합니다 |
| 다운로드 해시 불일치 | `~/.gradle/caches/poly-minecraft` | 해당 파일을 지우고 다시 실행합니다 |
| 월드 손상이 의심됨 | — | 이 버전은 월드 데이터를 바꾸는 API가 없습니다. 그래도 중요한 월드는 실행 전에 백업하세요 |
