# v0.3.0 현재 작업 인계

이번 변경은 저장소 main을 기반으로 codex/runtime-expression-v2 브랜치에서 진행했다. v1 패키지를 유지하면서 v2 선언적 표현 엔진과 ChatGPT/Claude 공용 제작 키트를 추가했다. 새로운 시작점은 AGENTS.md/CLAUDE.md, 엔진 상세는 docs/runtime-v2.md, 제작 진입점은 .agents/skills/music-widget-designer/SKILL.md다.

v2: 그룹, 회전/스케일/기울임/원근, opacity/마스크, 도형/gradient/stroke, 상태별 표시, 텍스트 align/valign/wrap/clip/ellipsis/marquee, 진행 표시 방향, 장식용 equalizer, 변형 target 터치. Dynamics/HybridHost는 정적과 동적 구간을 분리해 cropped bitmap을 native ViewFlipper에 전달한다. 실험적 단계별 재생이며 continuous animation이나 real audio capture를 의미하지 않는다. 절전/설정/메모리/주기에 따른 static fallback이 있다.

모든 새 디자인의 텍스트 폰트는 눈누에서 선택한다. v2 fonts provenance와 LICENSE.txt를 요구하며 실제 라이선스 해석은 AI가 원문을 읽고 판단한다. 새 Orbit 예제에는 눈누 SUITE와 원저작자의 OFL 전문을 동봉했다. 과거 v1 예제는 호환성을 위해 유지했다.

AI 제작은 python scripts/build.py THEME_DIR OUTPUT_DIR 한 번으로 package/font/layout 점검, 4개 상태 근사 이미지/contact-sheet/review.json을 생성한다. 이미지를 실제로 열어 최대3회 수정하는 절차를 명시했다. 연결된 개발용 Android 환경은 scripts/capture_android.py로 실제 Renderer와 AppWidgetHostView의 상태별 캡처를 받는다. 일반 ChatGPT/Claude 파일 실행만으로 Android가 있다고 가정하지 않는다.

현재 검증: Python8개, Android Robolectric native regression7개 통과. APK와 instrumentation APK 빌드 성공, lint 오류0/경고11. native 출력 이미지도 직접 열어 한글·마스크·회전·상태를 확인했다. 이 결과는 실제 기기 테스트가 아니다. 디스크 공간 부족으로 한 번 native 라이브러리 추출이 실패했고 이후 단일 worker 재실행에서 통과했다. 연결된 ADB 기기는 없었고 디스크 여유 부족으로 에뮬레이터를 실행하지 않았다. 실제 instrumentation capture와 One UI 홈 배치·YouTube Music 제어·재부팅 검증은 실행하지 않았다.

이전 AI_HANDOFF의 과거 검증 상태보다 이 문서와 README의 최신 지원 범위를 우선한다. 과거 이력은 원본 인계 문서에 남아 있다.
