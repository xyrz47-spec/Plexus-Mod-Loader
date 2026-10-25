# native-showcase-mod

Poly 모드 로더 Native API의 모든 영역을 한 모드에서 사용하는 예제입니다. 실제 26.2에서 동작을 검증하는 기준 모드이기도 합니다.

| 영역 | 내용 |
|---|---|
| 콘텐츠 | 빛나는 블록 `glow_block`(밝기 12, 곡괭이 필요, 사용 시 `spark` 지급), 아이템 `spark`, 음식 `star_fruit`, 창작 탭 배치 |
| 데이터 | 전리품 테이블, 레시피 2개, `#minecraft:mineable/pickaxe` 태그(`src/main/resources/data`) |
| 리소스 | 블록 상태, 모델, 아이템 모델, 텍스처, 영어·한국어 이름(`src/main/resources/assets`) |
| 명령어 | `/showcase info`, `/showcase add <a> <b>`(결과값 반환), `/showcase place <pos>`(권한 2) |
| 네트워크 | 채널 `native_showcase:greetings`. 접속 시 서버 → 클라이언트 환영, 클라이언트 → 서버 응답 |
| 이벤트 | `PlayerEvents.JOINED`, `BlockEvents.BROKEN`, `ClientEvents.TICK` |
| 상호작용 이벤트 | 보호 블록 `warded_block`(`InteractionContent`): 스파크 없이는 부술 수 없음(`BlockEvents.BREAK`, `EventPriority.HIGH`), 그 위에 놓기·사용·엔티티 사용·공격 거절(`PLACE`, `USE_BLOCK`, `USE_ENTITY`, `ATTACK_ENTITY`), 이름이 "Sealed"인 아이템 사용 거절(`USE_ITEM`), 놓인 위치 기록(`PLACED`). 게임 테스트 6개 |
| 생명주기 이벤트 | `LifecycleContent`: 서버 `STARTING`/`STOPPED`, 오버월드 `LOADED`/`SAVING`/`UNLOADED` 로그, 리스폰 횟수를 엔티티 데이터에 세기(`PlayerEvents.COPY`, `RESPAWNED`), `EntityEvents.JOINED_WORLD`·`DIED` 기록. 게임 테스트 2개(접속한 모의 플레이어의 사망·리스폰) |
| 태그 | `TagContent`: 코드로 `#native_showcase:warded`(보호 블록 판정에 사용), `#native_showcase:sparkly`, `#minecraft:mineable/pickaxe`에 항목 추가. `TagView`·`isBlockIn`·`isIn` 게임 테스트 2개 |
| 창작 탭 | 모드 탭 "Native Showcase"(`ContentRegistrar.creativeTab`): 빛나는 블록 아이콘, 모드 아이템과 자수정 조각. 창작 화면의 둘째 페이지 |
| 클라이언트 | 키 바인딩 `K`(액션 바 표시), HUD 레이어 |
| 게임 테스트 | 9개(`ShowcaseGameTests`) |

```powershell
.\gradlew.bat :poly-bootstrap:nativeApiGameTests      # 게임 테스트 9개 (헤드리스)
.\gradlew.bat :poly-bootstrap:clientWorldReloadTest   # 클라이언트: 키 바인딩·HUD·네트워크 왕복 (창이 열림)
.\gradlew.bat :poly-bootstrap:runClient               # 직접 플레이
```
