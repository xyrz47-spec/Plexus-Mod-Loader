# ADR-0015: Fabric 중첩 jar(jar-in-jar)

- 상태: 채택 (2026-10-21)
- 관련: ADR-0009(로더 브리지), ADR-0014(로더를 넘는 의존성과 링크 검사), ADR-0003(게임 클래스로더)

## 배경
Fabric 모드는 라이브러리와 하위 모듈을 jar 안의 jar(`fabric.mod.json`의 `jars`, 보통 `META-INF/jars/`)로 함께 배포합니다. ADR-0009는 이런 모드를 모두 차단했습니다(`POLY-COMPAT-103`). 실제 모드 묶음(`compat/mod-corpus-26.2.json`)에서는 Fabric 쪽 26개가 이 이유로 막혔고, 그중 `fabric-api`와 `fabric-language-kotlin`은 이것 하나 때문에 막혔습니다.

Fabric Loader는 중첩 jar를 각각 하나의 모드로 다룹니다. 확인한 규칙은 다음과 같습니다.

- 중첩 jar는 그 jar를 담은 모드가 선택될 때만 후보가 됩니다.
- `mods` 폴더에 직접 넣은 모드는 반드시 선택됩니다.
- 같은 모드의 중첩 사본이 여럿이면 가장 높은 버전 하나가 쓰입니다.

## 결정
1. 브리지는 `jars` 목록을 `BridgeAssessment.nestedJars`로 보고만 합니다. 중첩 jar가 있다는 사실만으로는 차단하지 않습니다. `POLY-COMPAT-103`은 폐기합니다. 버전 순서는 브리지가 정합니다(`ModLoaderBridge.compareVersions`, Fabric은 `Version.parse`).
2. 런타임(`NestedJarExtractor`)이 중첩 jar를 실행 폴더의 `.poly/nested-jars/<SHA-256 앞 16자>/<파일 이름>`에 풉니다.
   - 내용 해시로 저장하므로, 다음 실행과 같은 jar를 담은 다른 모드가 같은 파일을 씁니다.
   - 같은 브리지로 판정합니다. 중첩은 여러 겹을 따라갑니다(최대 8겹, 자기 자신을 담으면 차단).
   - 중첩 모드마다 출처를 적습니다(`POLY-COMPAT-120`, 경고).
   - `jars`에 적혔지만 없는 항목, 읽을 수 없는 jar, 너무 깊은 중첩은 바깥 모드를 차단합니다(`POLY-COMPAT-123`).
   - 그 로더의 모드가 아닌 중첩 jar는 `POLY-DISCOVERY-002`로 보고합니다.
3. `CrossLoaderModResolver`가 Fabric 규칙을 적용합니다.
   - 바깥 모드가 로드되지 않으면 안쪽 모드도 로드되지 않습니다(`POLY-COMPAT-121`).
   - 같은 ID가 여럿이면 우선순위는 이렇습니다: Native 모드 > 직접 설치한 모드 > 중첩 사본 중 가장 높은 버전. 나머지 사본은 로드하지 않습니다(`POLY-COMPAT-122`).
   - 선택된 사본이 다른 이유로 막히면 다음 사본을 고릅니다. 이 선택은 판정을 반복할 때마다 다시 합니다.
   - `POLY-COMPAT-117`(중복 ID 차단)은 직접 설치한 모드끼리만 적용합니다.
4. 바깥 모드는 로드되지만 그 안의 모드 일부가 로드되지 않으면 `POLY-COMPAT-124`(경고)를 붙입니다. Fabric 규칙으로는 바깥 모드가 로드되는 게 맞습니다. 다만 일부만 동작한다는 사실을 숨기지 않기 위해서입니다. 예: 하위 모듈 44개 중 43개가 막힌 `fabric-api`.
5. 로드되는 중첩 모드의 jar는 다른 모드와 똑같이 게임 클래스로더에 들어가고, 링크 검사를 받고, 의존 순서대로 엔트리포인트가 불립니다.

## 근거(실험, 2026-10-21)
- `nestedJars` 시나리오:
  - `fabric_bundle`이 담은 `fabric_nested_lib`와, 그 안에 한 겹 더 든 `fabric_nested_deep`이 로드됩니다. 두 겹 안쪽 클래스까지 불립니다.
  - 라이브러리 엔트리포인트가 바깥 모드보다 먼저 돕니다.
  - Mixin을 쓰는 중첩 모드만 따로 차단되고, 바깥 모드에 `124`가 붙습니다.
- 실제 모드 묶음:
  - 중첩 모드 142개를 새로 판정합니다. 막는 이유는 Mixin 77, 바깥 jar 차단 55, class tweaker 54입니다.
  - 직접 설치한 모드 중 로드되는 것은 `fabric-api`(하위 모듈 1/44, `124`) 하나입니다.
  - `fabric-language-kotlin`은 이제 중첩 jar가 아니라 Fabric Loader의 `LanguageAdapter` API(`111`)에서 막힙니다.
  - 예외와 게임 크래시는 0건입니다.

## 결과
- 중첩 jar를 쓰는 모드도 판정할 수 있게 됐고, 막히면 모듈 단위로 정확한 이유가 나옵니다.
- 실행 폴더 아래에 추출본이 쌓입니다. 내용이 같으면 다시 쓰지 않습니다. 오래된 추출본은 아직 정리하지 않습니다.
- 실제 인기 모드가 더 돌려면 Fabric Loader API(다음 단계)와 Mixin이 필요합니다.
