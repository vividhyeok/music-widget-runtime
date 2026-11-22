# v0.4.0 앱 사용성과 안정성

기존 테마 패키지·위젯별 테마·플레이어 선택·알림 접근을 유지하는 업데이트다. 앱 ID와 서명 체계, runtime SharedPreferences 키, 테마 디렉터리 구조를 변경하지 않는다. 이전 v1 패키지와 v2 엔진/AI 제작 키트를 유지한다.

## 수정한 동작
- 보관한 테마를 실제 Renderer로 미리 보여주는 목록과, 설정 적용 전 미리보기를 추가했다. 한 번에 네 개씩 표시해 Bitmap 메모리를 제한한다.
- 미리보기의 고정 렌더 예산, 작업 세대 번호, detach 취소/해제를 통해 이전 요청의 이미지가 뒤늦게 덮어쓰거나 화면 재생성 후 빈 상태로 남지 않게 했다. 로딩·실패·재시도를 표시한다.
- 가져오기는 Activity와 분리된 프로세스 범위 작업으로 실행한다. 화면 재생성 후에도 진행 상태와 결과를 받고, 중복 가져오기를 막고, 완료 후 URI 권한을 해제한다. 실제 검증 완료된 패키지만 공개하는 기존 원자적 가져오기를 유지한다.
- 테마/플레이어 선택을 ID로 보존하고, 선택한 플레이어가 종료되어도 대기 항목으로 유지한다. 저장 전에는 기존 홈 위젯 설정을 수정하지 않는다. 뒤늦은 목록 요청이 최신 선택을 덮어쓰지 않도록 검사한다.
- 손상되거나 사라진 테마 설명은 해당 항목만 제외하고 복구 안내를 표시한다. 기존 설정은 삭제하거나 다른 테마로 자동 대체하지 않는다. 폴더/manifest 크기와 필수 표시 메타데이터를 읽을 때도 확인한다.
- 런처에 previewImage/previewLayout과 초기 디자인 예시를 제공해, 첫 Bitmap 갱신 전 빈 미리보기를 줄였다. 이 정적 디자인 예시는 선택한 테마의 동적 미리보기와 구분한다.
- 홈 화면 추가 요청, 기존 위젯별 적용, 목록 새로고침, 움직임 설정을 제공한다. 알림 접근 메뉴나 파일 선택기를 열지 못해도 안내를 표시한다.
- 밝은 배경의 시스템 표시줄과 화면 재생성 후 스크롤 위치를 유지한다.

## 검증 방법
기존 RuntimeTest 7개와 SettingsUiTest 3개: 실제 패키지 렌더링/독립 터치/잘못된 ZIP/세션 격리/v2 효과·메모리 fallback에 더해, 미리보기 완료·화면 재생성·손상된 테마 격리·실패 후 재시도·저장하지 않은 선택 유지·종료된 플레이어 보존을 검사한다. Python 패키지 테스트 8개도 유지한다.

기존 사용자 데이터가 있는 기기에는 RuntimeSmoke 기본 suite를 실행하지 않는다. 기본 suite는 disposable emulator 전용이며 샘플 테마를 가져온다. 실제 기기의 비파괴 UI 점검은 아래 모드다.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode ui-check dev.mwidget.runtime.test/dev.mwidget.runtime.RuntimeSmoke
```

ui-check는 실제 MainActivity의 미리보기를 기다린 뒤 화면을 다시 생성하고 다시 기다린다. runtime 설정의 전후 동일성을 검사한다. 결과와 실제 화면 캡처는 앱 private files/qa-ui에 저장한다. 사용자 설정을 바꾸는 버튼을 누르거나 테마를 가져오지 않는다.

Galaxy S22에서 v0.2.0 → v0.4.0 무선 ADB 업데이트, 기존 서명 일치와 최초 설치 시각 유지, 테마 4개의 파일49개 해시 동일, runtime 설정 동일, 기존 홈 위젯2개 ID 동일을 확인했다. 실제 기기 UI 검사에서 미리보기4개가 화면 재생성 전후 모두 완료됐다. 모든 음악 앱의 동작이나 장시간 배터리 안정성을 인증하는 것은 아니다.

백업 APK·설정·사용자 테마·실기기 캡처는 로컬 build/device-update 안에만 보관한다. build와 APK는 기존 .gitignore 적용 대상이며 사용자 파일을 커밋하지 않는다.
