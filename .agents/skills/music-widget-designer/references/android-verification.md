# Android 근거 생성

`preview.py`와 `build.py`의 approx 이미지는 Pillow 근사다. Android Canvas 출력, 실제 AppWidgetHostView inflation/layout, 대상 런처의 실제 홈 배치는 서로 다른 검증이다. 결과를 섞어서 인증하지 않는다.

## 연결된 Android 환경
JDK17+, SDK35+, ADB와 연결 기기가 있는 개발 머신에서 저장소 루트:

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python .agents/skills/music-widget-designer/scripts/capture_android.py dist/orbit.mwidget output/android --serial SERIAL
```

Windows는 gradlew.bat를 사용한다. 기존 앱과 서명이 다르면 실패를 알리고 사용자 데이터가 삭제되는 재설치를 자동으로 하지 않는다. 테스트 APK는 개발 검증 전용이다. 캡처 입력은 run-as로 앱 private files에 보내고 instrumentation으로 패키지 가져오기와 동일한 검증을 거친다. 앱의 공개 네트워크/intent 실행 기능을 추가하지 않는다.

키트만 있는 환경에서도 이 APK 두 개가 설치되고 adb가 연결되어 있다면 `python scripts/build.py THEME OUTPUT --android-serial SERIAL`로 검증을 합칠 수 있다. `--adb`로 경로를 지정한다. APK 설치나 에뮬레이터 시작을 일반 AI 파일 실행 환경에서 가능하다고 가정하지 않는다.

출력: renderer-playing/paused/long-ko/missing/artwork.png, host-*.png, frame-N.png, android-report.json. renderer는 같은 Renderer.render를 사용한다. host는 실제 Android AppWidgetHostView에 실제 RemoteViews를 적용해 측정·배치·그린 결과다. 프레임 PNG는 선언적 타임라인의 샘플이며 실제 런처 애니메이션 녹화가 아니다. host 캡처는 홈 런처에 배치한 화면이 아니며 One UI 여백과 실제 타이머 동작을 보장하지 않는다.

## 기기 없는 개발 검증
`./gradlew testDebugUnitTest lintDebug`는 Robolectric의 Android native graphics와 RemoteViews inflation을 검사한다. 이 테스트 결과를 실제 기기 검증이라고 부르지 않는다. 기기가 없으면 미검증을 report에 유지한다.

## AI 시각 검토
원 레퍼런스와 생성된 상태별 이미지를 직접 열어 구도/비율/잘림/한글/대비/터치 위치를 확인한다. 최대3회 고치고 다시 같은 명령을 실행한다. 동적 효과는 정지·재생과 여러 timeMs 프레임 및 정적 fallback을 함께 검사한다. 실물 S22/One UI에서만 확인할 수 있는 항목을 짧게 구분한다. 실제 홈 배치·음악 앱 제어·재부팅 복구는 별도 smoke suite와 실제 기기 확인 대상이다.
