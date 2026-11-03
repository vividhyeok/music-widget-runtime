---
name: music-widget-designer
description: 자연어 요청으로 Music Widget Runtime의 PNG·폰트·레이어·터치 영역을 포함한 .mwidget 테마를 제작하거나 수정한다. Android 앱이나 범용 디자인 편집기를 변경하는 작업에는 사용하지 않는다.
---

사용자의 분위기, 색상, 가독성, 앨범아트 및 버튼 배치 요구를 독립적인 테마 디렉터리에 구현한다. 앱 Kotlin 코드를 수정하지 않는다.

먼저 [규격](references/schema.json)을 읽는다. `manifest.json`과 참조하는 PNG, TTF/OTF, `LICENSE.txt`만 포함한다. 외부 URL, 스크립트, SVG 및 실행 코드를 넣지 않는다. 글꼴과 이미지의 배포 권한을 확인하고 라이선스를 동봉한다.

배경은 실제 질감·그라데이션·일러스트 PNG로 제작한다. 재생·일시정지·이전·다음 아이콘 PNG를 별도 제공한다. 문자나 음악 메타데이터를 배경에 굽지 않는다. 앨범아트는 동적 artwork 레이어와 fallback PNG를 사용한다. 글꼴은 패키지에 포함하고 한국어 문자가 필요하면 해당 글리프를 지원하는 글꼴을 선택한다.

좌표는 canvas 논리 픽셀이다. 레이어 순서대로 합성하며 text는 한 줄, artwork는 중앙 crop, image/icon은 지정 사각형에 stretch한다. 색상은 #RRGGBB이다. toggle 아이콘에는 playingSrc를 둔다. 터치 영역은 독립적이며 서로 겹치지 않게 배치한다. 720×360 기준으로 96×96 이상의 주요 버튼 영역을 권장한다. 최종 S22 위젯 크기에 따라 48dp 이상인지 확인한다.

1. 새 테마 또는 기존 테마 복사본에 에셋과 manifest를 작성한다. 예제는 저장소 `themes/`를 참고한다.
2. `python scripts/pack.py THEME_DIR OUTPUT.mwidget`로 검증 후 패키징한다.
3. `python scripts/validate.py OUTPUT.mwidget`를 실행한다.
4. `python scripts/preview.py OUTPUT.mwidget preview.png`로 긴 제목·한국어·재생/일시정지 샘플을 확인한다. 미리보기는 Pillow와 Android의 글꼴 렌더링 차이가 있으므로 픽셀 동일성이나 실기기 동작을 보장하지 않는다.
5. Android 앱의 패키지 가져오기로 설치하고 대상 위젯 인스턴스에 적용한다. 동일 테마 ID를 가져오면 그 테마를 쓰는 인스턴스가 갱신된다. 다른 인스턴스를 유지하려면 새 ID를 쓴다.

기본 제한: ZIP 16MiB, 해제 24MiB, 64개 파일, 파일 8MiB, 압축비 200, 이미지 한 변 2048 및 2,097,152픽셀, 이미지 총 8,388,608픽셀, 폰트 2MiB. 미리보기 성공과 Galaxy S22의 음악 제어 검증을 구분해 보고한다.
