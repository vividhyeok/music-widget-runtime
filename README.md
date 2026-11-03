# Music Widget Runtime

Android 12(API 31) 이상에서 MediaSession 정보를 홈 화면 위젯에 표시하는 Kotlin 앱입니다. PC에서 만든 `.mwidget` 패키지를 Android 파일 선택기로 가져온 뒤 각 위젯에 적용합니다.

## Galaxy S22에서 사용하기

1. `dist/MusicWidgetRuntime-debug.apk`를 설치하고 앱을 한 번 엽니다. 이 APK는 개인 설치용 디버그 빌드입니다.
2. 앱의 **알림 접근 설정 열기**를 눌러 Music Widget Runtime을 허용합니다. 권한 선택이 제한되면 설정의 앱 정보에서 **제한된 설정 허용** 항목이 제공되는지 확인합니다. 메뉴 표시는 Android와 One UI 버전에 따라 다릅니다.
3. YouTube Music에서 곡을 재생합니다. 홈 화면을 길게 누르고 위젯 목록에서 **Music Widget Runtime**을 추가합니다. 처음에는 **Nocturne**을 선택합니다.
4. `dist/tape-deck.mwidget`을 휴대전화에 복사합니다. 앱의 **.mwidget 가져오기**에서 파일을 선택하고 두 번째 위젯을 추가해 **Tape Deck**을 적용합니다. 위젯의 `⋮` 또는 위젯 본문을 눌러 나중에 다시 선택할 수 있습니다.
5. 두 위젯의 제목·아티스트·앨범아트를 확인합니다. 이전·재생/일시 정지·다음 버튼을 개별로 누릅니다. 다른 MediaSession 플레이어도 활성화하고 전환 버튼과 위젯별 플레이어 선택을 확인합니다. 플레이어가 제공하는 세션과 동작만 사용할 수 있습니다.
6. 앱을 닫고 다음 곡을 재생합니다. 이어서 기기를 재부팅하고 잠금 해제한 뒤 음악을 재생하여 위젯 설정 복구와 표시·제어를 확인합니다.

자동 모드는 재생 중인 세션을 우선합니다. 고정 모드는 해당 플레이어가 잠시 사라져도 선택을 유지합니다. 전환 버튼은 현재 활성 플레이어와 자동 모드를 순환합니다. 알림 리스너는 알림 내용을 읽거나 저장하지 않으며, 활성 MediaSession을 조회하는 데 사용합니다.

## PC에서 테마 만들기

예시 요청은 “새벽 서울의 네온과 산 능선, 큰 민트색 재생 버튼” 및 “베이지 종이 질감의 빈티지 카세트, 갈색 헤더, 왼쪽 곡 정보와 오른쪽 앨범아트”입니다. 생성된 소스는 `themes/nocturne`과 `themes/tape-deck`에 있습니다. 테마마다 배경과 앨범아트 대체 PNG, 버튼 PNG, 글꼴과 라이선스를 포함합니다.

```powershell
python -m pip install Pillow jsonschema
$scripts = '.agents/skills/music-widget-designer/scripts'
python "$scripts/pack.py" themes/tape-deck dist/tape-deck.mwidget
python "$scripts/validate.py" dist/tape-deck.mwidget
python "$scripts/preview.py" dist/tape-deck.mwidget dist/tape-deck.png --title '서울의 밤' --paused
python tools/test_packages.py
```

제작 규칙은 `.agents/skills/music-widget-designer/SKILL.md`에서, JSON 구조는 `references/schema.json`에서 확인할 수 있습니다. 동일한 `id`의 테마를 가져오면 해당 ID를 사용하는 위젯이 새 디자인을 표시합니다. 원본과 별개로 선택하려면 새 ID를 사용합니다.

`.mwidget`은 루트의 `manifest.json`과 참조된 에셋, 선택적 `LICENSE.txt`로 구성됩니다. v1은 이미지·앨범아트·텍스트·상태 전환 아이콘을 배열 순서로 합성합니다. 독립된 버튼 사각형은 이전·재생/일시 정지·다음·세션 전환·설정 중 하나에 연결됩니다. 앱은 경로 정규화, 중복 경로, 스트리밍 크기·압축비, CRC, 이미지 해상도와 총 픽셀, 글꼴 및 스키마를 가져오기 전에 확인합니다. 설치된 패키지와 위젯 설정은 앱 내부에 저장합니다.

## 빌드와 자동 검증

JDK 17 이상과 Android SDK Platform 35가 필요합니다. Windows PowerShell에서 SDK/JDK 경로를 실제 설치 경로로 지정한 뒤 실행합니다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew.bat :app:testDebugUnitTest :app:lintDebug
./tools/verify-emulator.ps1 -Serial emulator-5554
```

`verify-emulator.ps1`은 테스트용 AVD에 디버그 APK와 테스트 APK를 설치하고, 알림 접근과 테스트 호스트의 위젯 바인딩을 허용합니다. 테스트는 실제 Android AppWidgetHost와 테스트 MediaSession을 사용합니다. S22 수동 검증은 위의 사용 순서를 따릅니다.

원본 조사와 재사용 범위는 `docs/upstream.md`, 원본 MIT 고지는 `docs/UPSTREAM-LICENSE.txt`를 참고하세요.
