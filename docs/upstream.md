# 원본 조사

조사 대상은 [Meo98/media-widget](https://github.com/Meo98/media-widget/tree/1f6336013973dac6b7ea8245de3744f7c64e56ac)의 `1f6336013973dac6b7ea8245de3744f7c64e56ac`입니다. MIT 라이선스이며 원문을 `UPSTREAM-LICENSE.txt`에 보존했습니다.

| 원본 | 조사 결과와 적용 |
| --- | --- |
| `NotifListener.kt`, `MediaSessionTracker.kt` | 시스템 바인딩에 맞춰 활성 세션 리스너를 붙이고, 세션 토큰별 메타데이터·재생 상태 콜백을 등록·해제합니다. 세션 목록이 바뀔 때 구독을 다시 묶고 콜백 갱신을 짧게 지연하는 구조를 `Sessions.kt`에 적용했습니다. |
| `MediaState.kt`, `MediaWidgetProvider.kt` | 활성 세션 우선순위와 위젯 ID별 제어 경로를 확인했습니다. 위젯이 지정한 세션을 잃은 상태에서 다른 앱으로 명령이 넘어가지 않게 고정 모드를 구현했습니다. |
| `widget_*.xml`, `StyleAssets.kt`, `SettingsRepo.kt` | 위젯 모양을 APK 리소스와 레이아웃 버킷으로 선택합니다. `.mwidget`을 위한 파일 검증, Bitmap 렌더러, 터치 영역과 테마 선택 저장소를 새로 만들었습니다. |

원본의 `assembleDebug testDebugUnitTest`를 시도했습니다. 원본 래퍼의 Gradle 8.11.1 배포본 압축 해제와 로컬 Gradle 8.13을 사용한 캐시 변환이 Windows 디스크 공간 부족으로 중단됐습니다. 원본 APK의 실행은 확인하지 못했습니다. 런타임은 별도 패키지 `dev.mwidget.runtime`으로 빌드하고 검증합니다.

세션 조회와 리스너의 관계는 [Android MediaSessionManager API](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName))를 참고했습니다.
