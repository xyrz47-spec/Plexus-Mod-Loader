# poly-game-api

## 이 모듈이 하는 일
Poly 모드가 게임 내용을 다루는 공개 API입니다. Minecraft 타입을 노출하지 않습니다. 모든 타입은 `PREVIEW`입니다.

| 패키지 | 내용 |
|---|---|
| `dev.polymodloader.api.resource` | `ResourceId`(`namespace:path`) |
| `dev.polymodloader.api.content` (+ `.block`, `.item`) | 블록·아이템 정의, 동작 콜백, 창작 탭, 핸들 |
| `dev.polymodloader.api.world`, `.entity` | `WorldView`, `EntityView`, `PlayerView`, `BlockPos`, `InteractionResult` |
| `dev.polymodloader.api.event.player`, `.event.block` | `PlayerEvents.JOINED/LEAVING`, `BlockEvents.BROKEN` |
| `dev.polymodloader.api.command` | 명령어 트리(`CommandNode`), 인수 타입, 컨텍스트 |
| `dev.polymodloader.api.network` | 페이로드 채널, 코덱, 읽기·쓰기 |
| `dev.polymodloader.api.client` | 키 바인딩, HUD 레이어, 클라이언트 틱 이벤트 |
| `dev.polymodloader.api.devtools.gametest` | 게임 내 테스트 등록과 테스트 컨텍스트 |

## 무엇을 소유하는가
API 계약, 값 검증(정의 레코드 생성자), JavaDoc의 시점·스레드·실패 규칙.

## 무엇을 하지 않는가
- 선언을 저장하거나 검증하지 않습니다. 그 일은 `poly-runtime-impl`의 `NativeRegistrations`가 합니다.
- 게임에 연결하지 않습니다. 그 일은 `poly-game-v26_2`가 합니다.

## 의존성
`poly-runtime-api`, `poly-event-api`. 모드는 `poly-lifecycle-api`를 통해 이 모듈을 전이적으로 받습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-game-api:check
.\gradlew.bat :poly-bootstrap:nativeApiGameTests   # 실제 게임에서 API 동작 확인
```

## 대표 흐름
1. 엔트리포인트에서 `mod.content().block(...)`, `mod.commands().register(...)` 등을 호출합니다.
2. 게임이 레지스트리를 동결하기 직전에 Poly 모드 로더가 블록·아이템을 만듭니다.
3. 동작 콜백과 이벤트는 게임 스레드에서 뷰 객체와 함께 호출됩니다.

## 알려진 제한
[docs/NATIVE_API.md](../docs/NATIVE_API.md)의 "아직 없는 것"과 [ADR-0010](../docs/adr/0010-native-api-game-layer.md)의 제약을 보세요.
