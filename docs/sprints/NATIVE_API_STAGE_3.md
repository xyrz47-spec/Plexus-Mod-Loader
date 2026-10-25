# Native API 3단계 — 남은 심화 항목 (2026-10-19)

2단계 끝에 `docs/NATIVE_API.md` "아직 없는 것"에 남긴 항목을 묶음별로 구현합니다. 각 묶음은 실제 Minecraft 26.2에서 검증한 뒤 커밋합니다.

## 묶음 A — 콘텐츠 심화

| 기능 | 검증 |
|---|---|
| 이름 블록 속성, 상태별 빛 | 게임 테스트 `choice_property_and_light`, `choice_property_reads_game_blocks`(바닐라 계단 `half`) |
| 칸 배치 메뉴·진행 막대 | 게임 테스트 `menu_layout_grinder`(40틱 뒤 자갈 1개). 실제 클라이언트에서 메뉴 화면 열림(`Opened mod menu native_showcase:grinder with 2 slot(s)`) |
| 아이템 데이터 컴포넌트 | 게임 테스트 `item_component_roundtrip`(값·이름 왕복, 제거) |
| 투사체 | 게임 테스트 `projectile_hits_entity`(맞은 몹이 빛남, 탄환 제거) |
| 자연 스폰 | 게임 테스트 `natural_spawns_in_plains`(평원 생물 목록에 모드 몹과 소가 함께 있음) |

| 전체 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **31개 통과**(6개 추가) |
| `clientWorldReloadTest` | 통과(새 줄 3개 포함 25줄 확인) |
| `serverBootSmokeTest`, `worldCreateSaveTest`, `compatibilityMatrix` 6/6 | 통과 |
| `check` | 단위 테스트 118개 통과 |

## 묶음 B — 게임플레이

| 기능 | 검증 |
|---|---|
| AI 목표 | 게임 테스트 `custom_goal_seeks_glow_block`(골렘이 5칸 떨어진 발광 블록까지 걸어감), `mob_view_target` |
| 피해량 수정 | 게임 테스트 `modify_damage_halves_magic`(마법 피해 8 → 4) |
| 속도·중력 | 게임 테스트 `velocity_and_gravity`(중력을 끄고 위로 밀자 10틱 동안 1칸 넘게 상승) |
| 탑승 | 게임 테스트 `riding_and_passengers`. 실제 클라이언트에서 플레이어가 골렘에 탄 상태가 클라이언트에 보임 |
| 발전 과제 | 게임 테스트 `advancement_trigger`(트리거 → 완료, 취소, 부여) |

| 전체 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **37개 통과**(6개 추가) |
| `clientWorldReloadTest` | 통과(27줄 확인) |
| `check` | 단위 테스트 120개 통과 |

조종(이동 키로 탈것 몹 움직이기)은 키 입력을 자동화할 수 없어 실제로 눌러 보지 않았습니다. 말과 같은 경로(`getControllingPassenger`, `getRiddenInput`, `tickRidden`)를 씁니다.

## 묶음 C — 월드

| 기능 | 검증 |
|---|---|
| 코드 바이옴 | 게임 테스트 `code_biome_is_registered`(`/fillbiome` 후 `biomeAt`이 `native_showcase:glow_meadow`) |
| 코드 지형 + 코드 바이옴 차원 | 실제 클라이언트 월드에서 `native_showcase:terraces` 차원의 (0, 65, 0)이 생성기가 놓은 발광 블록이고 바이옴이 `glow_meadow`. "실험적 설정" 화면 없음 |
| 청크 강제 로드·로드 이벤트 | 게임 테스트 `chunk_forcing_and_events`(640블록 떨어진 청크를 강제 로드 → 로드됨, `CHUNK_LOADED` 수신, 해제) |

| 전체 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **39개 통과**(2개 추가) |
| `clientWorldReloadTest` | 통과(28줄 확인) |
| 변환 규칙 | 서버 23개 수락(`poly:chunk_loaded`, `poly:chunk_unloaded` 추가) |
| `check` | 단위 테스트 122개 통과 |

청크 언로드 이벤트는 실행 경로만 연결했고, 언로드 시점이 비동기라 게임 테스트로 확인하지 않았습니다.

## 묶음 D — 클라이언트 그리기

모두 실제 클라이언트(`clientWorldReloadTest`, 38줄 확인)에서 확인했습니다.

| 기능 | 확인한 로그 |
|---|---|
| 몹 상자 모델 | `Drew mod mob model native_showcase:wisp (2 animated part(s))` |
| 블록 엔티티 그리기 | `Drew block entity renderer of native_showcase:grinder (2 draw(s))`(넣은 조약돌과 진행률 글자) |
| 모드 파티클 | `Added 1 mod particle provider(s)`, 서버가 보낸 파티클을 만들 때 `Created mod particle native_showcase:sparkle` |
| 동적 텍스처 | `Dynamic texture uploaded: native_showcase:dynamic/noise`(HUD에 그림) |
| 시야각·시점 | `Field of view event received`, `Camera perspective: THIRD_PERSON_BACK` |
| 후처리 셰이더 | 모드 조각 셰이더 `native_showcase:post/sepia`를 쓰는 효과가 불러와져 켜졌다가 꺼짐 |

| 전체 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | 39개 통과 |
| 변환 규칙 | 클라이언트 전용 3개 추가(`poly:client_particle_providers`, `poly:client_block_entity_renderers`, `poly:client_fov`) |
| `check` | 단위 테스트 124개 통과 |

그리기 결과가 화면에 올바르게 보이는지(모양·색)는 화면을 캡처하지 않으므로 사람이 봐야 합니다. 로그는 게임이 그 경로를 실제로 실행했다는 뜻입니다.

## 묶음 E — 리소스·네트워크·운영

| 기능 | 검증 |
|---|---|
| 데이터 리로드 | 게임 테스트 `reload_listener_reads_mod_data`(하위 디렉터리 포함 2개). 실제 클라이언트에서 `Loaded 2 showcase message(s)` |
| 에셋 리로드 | 실제 클라이언트에서 `Loaded 1 client tip(s)` |
| 엔티티 데이터 | 게임 테스트 `entity_data_is_saved_with_the_entity`(`/execute if data entity`로 저장 데이터에 들어간 것 확인). 실제 클라이언트가 동기화된 방문 횟수를 읽음 |
| 저장소 버전·동기화 | 게임 테스트 `versioned_world_storage`, 단위 테스트(옛 버전 마이그레이션, 예약 키 숨김). 실제 클라이언트가 서버 저장소 사본을 읽음 |
| 권한 | 게임 테스트 `permission_nodes_and_providers`(기본 레벨 거부, 공급자 허용) |
| 연결 이벤트 | 실제 클라이언트에서 접속 이벤트, 데모 끝의 `leaveWorld` 뒤 퇴장 이벤트 |
| 월드 백업 | 수동 실행: 원본 월드의 모드 기록을 옛 버전·빠진 모드로 바꾸고 `clientWorldReloadTest`를 돌리자 `Backed up the world before loading it with changed mods [native_showcase 0.0.9->0.1.0, old_mod 1.0->removed]`와 `backups/…poly_roundtrip.zip`(약 250KB)이 생김. 단위 테스트로 판정 규칙 확인 |

| 전체 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **43개 통과**, 10회 연속 통과 |
| `clientWorldReloadTest` | 통과(44줄 확인) |
| 변환 규칙 | 서버 27개(4개 추가), 클라이언트 전용 3개 추가 |
| `check` | 단위 테스트 127개 통과 |

발견하고 고친 문제:
- **겹치는 게임 테스트:**
  - 게임은 1칸짜리 테스트 구역을 6~7칸 간격으로 놓습니다. 그래서 더 넓게 쓰던 테스트(광석 배치, 걷기 목표, 바이옴 채우기)가 이웃 테스트와 겹쳐 가끔 실패했습니다.
  - `TestArea`로 테스트별 빈 구조물 크기를 지정하게 했습니다.
  - 바이옴 테스트는 게임이 바이옴 경계를 한 칸 흐리게 읽는 점을 고려해 넓게 채웁니다.
- **퇴장 이벤트 검증:** 클라이언트 테스트는 마지막에 프로세스를 종료해서 월드를 떠나지 않습니다. 자동 실행에서는 쇼케이스가 데모 끝에 `leaveWorld`를 부르도록 했습니다(`-Dnative_showcase.leaveAfterDemo=true`).

## 묶음 A·B에서 발견하고 고친 문제
- **블록 상태 번호:** 게임은 블록 상태 번호(`Block.BLOCK_STATE_REGISTRY`)와 상태 캐시를 `Blocks` 정적 초기화에서만 만듭니다.
  - 1단계부터 모드 블록 상태가 빠져 있었습니다. 게임 테스트는 패킷을 보내지 않아 드러나지 않았습니다.
  - 실제 클라이언트에 분쇄기를 놓자 `Can't find id for 'PolyBlock[]'`로 연결이 끊겼습니다. 등록 직후 번호와 캐시를 만들도록 고쳤습니다.
- **모의 플레이어 메뉴:** 게임 테스트의 모의 플레이어는 연결이 없어 `openMenu`가 아무것도 하지 않습니다. 메뉴 객체를 직접 연결하도록 했습니다.
