# plexus-mod-metadata

## 이 모듈이 하는 일
`poly.mod.json` 스키마 1을 파싱·검증하고, SemVer 2.0.0 버전과 버전 범위를 제공합니다.

## 무엇을 소유하는가
`dev.plexusmodloader.runtime.metadata`(`ModMetadata`, `ModId`, `ModMetadataReader`, `MetadataProblemCode`)와 `...metadata.version`(`SemanticVersion`, `VersionRange`)

## 무엇을 하지 않는가
파일 검색(discovery)과 모드 사이 관계(의존성 해석)는 다루지 않습니다.

## 의존성
Gson 2.14.0(Minecraft 26.2와 같은 버전, ADR-0005)

## 주요 진입점
`ModMetadataReader.read(String json)` → `Result(metadata, problems)`

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-mod-metadata:check
```

`ModMetadataReaderTest` 19개, `VersionRangeTest` 18개

## 대표 흐름
JSON을 파싱한 뒤 알 수 없는 필드를 경고하고, 필드별로 검증해 모든 문제를 모읍니다. 오류가 하나도 없을 때만 `ModMetadata`를 만듭니다.

## 알려진 제한
`provides`는 예약 필드이며, 비어 있지 않으면 `PLEXUS-METADATA-010`으로 거부합니다. 범위 문법은 비교 연산자 조합과 `*`만 지원하고 `~`, `^`는 지원하지 않습니다.
