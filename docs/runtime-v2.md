# v2 구현과 확장 지점

Music Widget Runtime은 AI가 만든 선언적 음악 플레이어 디자인을 Android 홈 위젯에서 실행하는 런타임이다. 디자인 제작은 APK와 분리한다. 레퍼런스 → 폰트·에셋 선택 → 선언적 장면 → 패키지 검증 → 상태별 렌더링 → AI 시각 검토·수정 → .mwidget 전달 흐름을 제공한다.

## 현재 구현
- ThemeStore: v1/v2 폐쇄형 검증, 기존 ZIP/PNG/font 보안 한도 유지, v2 폰트 출처·그룹·효과 검증. 네트워크/코드 실행 없음.
- Scene: 부모부터 누적되는 공통 변환과 target 터치 사각형. 순서·pivot·원근을 렌더러와 터치가 공유한다.
- Renderer: Android Canvas 장면 합성. timeMs와 animate를 명시적으로 받아 캡처와 타임라인을 재현한다. 이미지 캐시는 한 프레임 렌더 수명에만 유지한다.
- Dynamics: 재생 상태, 사용자 움직임 설정, 시스템 애니메이션 설정, 절전 상태, 공통 주기로 실행 경로 선택.
- HybridHost: 순서대로 정적 구간과 동적 루트 구간 분리. 투명 영역을 잘라 필요한 bitmap만 RemoteViews에 전달. ViewFlipper가 호스트에서 느린 프레임을 순환한다. 최대3개 동적 구간·12프레임·최소500ms·반응형 크기 합계900000픽셀. 초과 시 전체 정적 합성으로 안전하게 돌아간다. 정적 레이어를 각 프레임마다 중복 전송하지 않는다.
- MusicWidget: 동일 Geometry로 Bitmap과 터치 배치. 이미지 교체에는 full update를 사용한다. bitmap을 partial update로 누적하지 않는다.
- Sessions: MediaSession 변경에 반응. 진행 표시/시간이 필요한 테마만 약5초 갱신. 화면이 꺼지면 렌더링을 생략하고 화면이 켜질 때 다시 갱신한다. 오디오 캡처 권한을 요청하지 않는다.
- 제작 키트: provider에 독립적인 Python CLI. 스키마, 폰트 출처, 글리프, 터치, 상태별 근사 이미지와 review.json. Android instrumentation 입력/캡처 도구 포함.

## 의도적으로 분리한 지원 수준
static 표현은 Canvas가 지원하고 임의의 정적 장식은 PNG로 자유롭게 합성할 수 있다. 런처의 RemoteViews에는 일반 앱 View나 Lottie/WebView를 그대로 넣을 수 없으므로 외부 애니메이션 라이브러리를 넣는 것만으로 홈 위젯 제약을 해결할 수 없다. 현재 움직임은 부드러운 연속 애니메이션이 아닌 실험적 단계별 프레임이다. UI 구성·마키·비주얼라이저의 모든 가능한 동작을 보장한다고 설명하지 않는다.

실제 오디오 FFT/파형, 드래그 탐색, 고프레임 회전은 미지원이다. 실제 캡처를 추가할 때 MediaSession을 오디오 소스로 오인하지 않고 별도 권한과 앱별 정책을 검증해야 한다. 사용자 동의 없이 캡처를 켜지 않는다. capabilities.json이 AI에게 이 경계를 알려준다.

## 검증
`python tools/test_packages.py`
`gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`
`python tools/build_ai_kit.py`

패키지 유효성, 변환과 터치 일치, 마스크/alpha, 재생·정지 타임라인, 메모리 fallback, native RemoteViews 적용을 회귀 검사한다. Robolectric native graphics는 실제 Android 기기/런처와 구분한다. 연결 기기용 ThemeCapture는 실제 Renderer와 AppWidgetHostView를 캡처한다. One UI 실제 홈, 사용자 음악 앱 제어와 재부팅은 별도 검증이다. 실행하지 않은 검증은 통과했다고 기록하지 않는다.

## 앞으로 기능을 추가할 때
Android ThemeStore/Renderer/Scene과 Python schema/validate/preview/scene을 함께 바꾸고 양쪽 유효·거부 입력을 검사한다. capability 문서와 v2 예제를 동기화한다. Python 미리보기에는 글꼴·래스터화 차이가 있으므로 Android 캡처를 최종 근거로 우선한다. 자동 검토는 오류 발견과 AI 시각 평가를 구분하며, 미학적 충실도를 스크립트 하나로 인증하지 않는다.

오픈소스는 호환성·라이선스·실제 필요성을 검토해 사용한다. 원본 Meo98/media-widget의 MIT 고지는 기존 docs/upstream.md/UPSTREAM-LICENSE.txt를 유지한다. 새 SUITE 폰트는 눈누 https://noonnu.cc/font_page/1150 에서 선택했고 sun-typeface/SUITE의 커밋 a114fec078bfcbdc93a7b2190510ab83de2a2d40 원본과 SIL OFL 고지를 동봉했다. 외부 애니메이션 라이브러리를 사용했다고 주장하지 않는다.
