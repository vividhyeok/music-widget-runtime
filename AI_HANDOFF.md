# Music Widget Runtime 작업 인계

## 사용자가 요청한 결과

Samsung Galaxy S22에서 YouTube Music 등 Android MediaSession을 사용하는 플레이어의 곡 정보와 재생 제어를 홈 화면 위젯에 제공하는 개인용 앱을 개발해 달라는 요청이었다. Android 12 이상을 대상으로 Kotlin과 Android 공식 API를 사용한다.

핵심 제약은 PC에서의 디자인 제작과 Android에서의 실행을 완전히 분리하는 것이다. PC의 AI가 자연어 디자인 요구를 받아 배경 PNG, 아이콘 PNG, 글꼴, 레이어와 터치 좌표를 담은 `.mwidget` ZIP을 만든다. Android 앱은 파일을 가져와 재생 정보와 디자인을 Bitmap으로 합성하고, 독립된 버튼 터치 영역을 제공한다. 테마 교체에는 APK 재설치가 필요 없어야 하고, 위젯마다 테마와 플레이어를 선택할 수 있어야 한다. 앱 UI는 알림 접근 안내, 가져오기, 위젯 설정에 집중한다.

추가 요구는 플레이어 전환, 이전·재생/일시 정지·다음 제어, 재부팅 후 복구, 오류 표시, 경로 탈출·압축 폭탄·과대 이미지 검증이었다. 원본 `Meo98/media-widget`을 먼저 조사하고, 실제로 확인한 재사용 가능 부분만 활용하도록 했다. 완료 기준은 자연어로 만든 테마가 실제 S22 홈 화면에 표시되고 음악 제어까지 되는 것이다. 기기를 연결할 수 없으면 빌드와 자동 테스트를 수행하고 기기 미검증 항목을 구분한다.

## 압축 파일 구조

| 경로 | 내용 |
| --- | --- |
| `AI_HANDOFF.md`, `README.md` | AI 인계 문서와 설치·개발 가이드 |
| `app/src/main/` | Kotlin 런타임, AndroidManifest, 위젯 리소스와 내장 Nocturne |
| `app/src/test/` | PC에서 실행하는 Robolectric 회귀 테스트 |
| `app/src/androidTest/` | 실제 Android 위젯 호스트와 테스트 MediaSession을 사용하는 기기 테스트 |
| `.agents/skills/music-widget-designer/` | 자연어 제작 규칙, JSON 스키마, 검증·미리보기·패키징 스크립트 |
| `themes/nocturne/`, `themes/tape-deck/` | 수정 가능한 JSON, PNG, 글꼴과 라이선스 |
| `dist/` | 서명된 디버그 APK, 두 테마 파일과 PNG 미리보기 |
| `device-test/` | 기기 테스트용 APK와 실행 절차 |
| `tools/` | 테마·스키마 재생성, PC 테스트, 에뮬레이터 테스트 스크립트 |
| `docs/` | 원본 분석, 커밋 링크, MIT 라이선스 |
| `gradle/`, 루트 Gradle 파일 | 프로젝트 설정과 Gradle Wrapper |

프로젝트 경로 구조를 그대로 유지했다. 압축을 푼 폴더를 프로젝트 루트로 연다. 점으로 시작하는 `.agents`도 ZIP에 포함된다. 로컬 SDK, Gradle 캐시, Git 이력, 디버그 키 저장소, 복제한 원본 저장소는 빌드 환경에 속한다. 원본은 `docs/upstream.md`의 커밋에서 다시 받을 수 있다.

## 설계와 코드 읽는 순서

1. `.agents/skills/music-widget-designer/references/schema.json`에서 패키지 형식을 확인한다. v1은 `manifest.json`에 논리 캔버스 크기와 순서가 있는 레이어, 겹치지 않는 터치 영역을 정의한다. PNG와 TTF/OTF는 ZIP에 담는다.
2. `ThemeStore.kt`에서 압축 크기, 파일 개수, 경로, CRC, 이미지 크기, 폰트, 스키마를 검증하고 성공한 테마만 공개하는 흐름을 확인한다. 같은 테마 ID를 다시 가져오면 해당 ID를 쓰는 위젯이 갱신된다.
3. `Renderer.kt`는 그림·앨범아트·텍스트·아이콘을 Canvas에 합성한다. `MusicWidget.kt`는 Bitmap과 버튼별 RemoteViews를 위젯에 적용하고 위젯 ID별 설정과 명령을 연결한다.
4. `Sessions.kt`는 알림 접근을 기반으로 활성 MediaSession을 추적한다. 자동 선택은 재생 중인 앱을 우선한다. 특정 앱을 지정하면 그 세션이 없어도 다른 앱에 제어 명령을 보내지 않는다. `SettingsActivities.kt`에는 패키지 가져오기와 위젯별 설정 화면이 있다.
5. Nocturne은 새벽 서울의 야경과 민트색 버튼을 테마로 삼았다. Tape Deck은 종이 질감, 카세트, 좌측 곡 정보와 우측 아트를 사용한다. Tape Deck은 런타임 APK에 포함되지 않은 외부 패키지다.

## 검증 상태와 다음 작업

- 설치 APK와 기기 테스트 APK를 빌드했다. 설치 APK의 디버그 서명을 검증했다.
- Python 패키지 테스트 5개와 Robolectric 테스트 3개를 통과했다. 두 테마의 검증·합성, 독립 버튼 배치, 같은 ID의 테마 교체, 앱별 세션 선택, 비정상 ZIP 차단을 확인했다. Android Lint는 오류 0건, 경고 8건이다.
- Pixel 5 AVD와 별도 테스트 AVD는 호스트 저장 공간 부족으로 부팅을 마치지 못했다. 마지막 ADB 조회에 활성 기기가 없었다. 기기 테스트 APK는 빌드했지만 실행하지 못했다.
- S22에서 실제 설치, 알림 접근 허용, One UI 홈 위젯 배치, 외부 Tape Deck 가져오기, YouTube Music 곡 정보와 제어, 잠금 해제 후 재부팅 복구를 확인해야 한다. 에뮬레이터 테스트가 성공하더라도 S22 결과와 구분해 기록한다.
- 원본 저장소는 커밋 단위로 조사했다. 원본 빌드 시도는 Windows 저장 공간 부족으로 중단됐으므로 원본 APK의 작동은 확인하지 못했다. 세션 구독 구조를 참고했고 별도 런타임 APK를 빌드했다.

다른 AI에게 이 ZIP을 전달할 때는 “먼저 AI_HANDOFF.md와 README.md를 읽고, 현재 ADB 상태를 확인한 뒤 남은 기기 검증을 이어가 줘. 테마 수정은 music-widget-designer 스킬을 따라 별도 .mwidget으로 만들어 줘”라고 요청하면 된다.
