---
name: music-widget-designer
description: 자연어 요청으로 Music Widget Runtime의 PNG·폰트·레이어·터치 영역을 포함한 .mwidget 테마를 제작하거나 수정한다. Android 앱이나 범용 디자인 편집기를 변경하는 작업에는 사용하지 않는다.
---

사용자의 분위기, 색상, 가독성, 앨범아트 및 버튼 배치 요구를 독립적인 테마 디렉터리에 구현한다. 앱 Kotlin 코드를 수정하지 않는다.

먼저 [규격](references/schema.json)을 읽는다. `manifest.json`과 참조하는 PNG, TTF/OTF, `LICENSE.txt`만 포함한다. 외부 URL, 스크립트, SVG 및 실행 코드를 넣지 않는다. 글꼴과 이미지의 배포 권한을 확인하고 라이선스를 동봉한다.

배경은 요청에 맞춰 PNG로 제작하거나 투명하게 생략한다. 선택한 동작의 아이콘만 PNG로 제공한다. 문자나 음악 메타데이터를 배경에 굽지 않는다. 앨범아트는 동적 artwork 레이어와 fallback PNG를 사용한다. 글꼴은 패키지에 포함하고 한국어 문자가 필요하면 해당 글리프를 지원하는 글꼴을 선택한다.

좌표는 canvas 논리 픽셀이다. 레이어 순서대로 합성하며 text는 한 줄, artwork는 중앙 crop, image/icon은 지정 사각형에 stretch한다. 색상은 #RRGGBB이다. toggle 아이콘에는 playingSrc를 둔다. 터치 영역은 독립적이며 서로 겹치지 않게 배치한다. 720×360 기준으로 96×96 이상의 주요 버튼 영역을 권장한다. 최종 S22 위젯 크기에 따라 48dp 이상인지 확인한다.

1. 새 테마 또는 기존 테마 복사본에 에셋과 manifest를 작성한다. 예제는 ZIP의 `examples/`를 참고한다.
2. `python scripts/pack.py THEME_DIR OUTPUT.mwidget`로 검증 후 패키징한다.
3. `python scripts/validate.py OUTPUT.mwidget`를 실행한다.
4. `python scripts/preview.py OUTPUT.mwidget preview.png`로 긴 제목·한국어·재생/일시정지 샘플을 확인한다. 미리보기는 Pillow와 Android의 글꼴 렌더링 차이가 있으므로 픽셀 동일성이나 실기기 동작을 보장하지 않는다.
5. Android 앱의 패키지 가져오기로 설치하고 대상 위젯 인스턴스에 적용한다. 동일 테마 ID를 가져오면 그 테마를 쓰는 인스턴스가 갱신된다. 다른 인스턴스를 유지하려면 새 ID를 쓴다.

기본 제한: ZIP 16MiB, 해제 24MiB, 64개 파일, 파일 8MiB, 압축비 200, 이미지 한 변 2048 및 2,097,152픽셀, 이미지 총 8,388,608픽셀, 폰트 2MiB. 미리보기 성공과 Galaxy S22의 음악 제어 검증을 구분해 보고한다.

사용자가 추가 지시하기 전까지 Pinterest 등 제공된 레퍼런스의 구도, 색상, 질감과 비율을 최대한 충실히 재현한다. 캔버스 비율은 레퍼런스에서 정하며 2:1로 고정하지 않는다. 음악 정보와 제어를 구도 안에 자연스럽게 배치한다. 한글 글꼴은 눈누 https://noonnu.cc/index 에서 우선 탐색하고 원 저작자의 패키지 재배포 허용 여부와 라이선스를 확인한다. 추가 동작 rewind/forward는 10초 이동, stop은 정지이며 플레이어가 지원할 때만 동작한다.


## 기능 선택과 최소 구성
사용자 요청과 레퍼런스가 기능 수보다 우선한다. 지원 기능을 전부 넣지 않는다.
미니멀 요청은 곡 제목과 재생/일시정지를 출발점으로 삼고, 이미지 중심 요청은 메타데이터와 버튼을 더 줄일 수 있다.
아티스트, 앨범아트, 이전/다음, 플레이어 전환, 시간, 진행 막대, 10초 이동, 정지는 각각 선택 사항이다.
요청하지 않은 기능을 빈자리를 채우기 위해 추가하지 않는다. 터치 대상 없는 정보만의 테마도 허용한다.
진행 막대는 type=progress, color/trackColor를 사용한다. 시간은 text의 elapsed/duration 필드다.
선택 기능을 생략하면 해당 레이어와 touch 항목도 생략한다. 설정은 앱에서도 열 수 있다.
작은 크기에서 버튼이 겹치거나 읽기 어려우면 기능 수를 줄인다. 투명 여백을 허용하며 고정 비율이나 카드 테두리를 강제하지 않는다.

## 다른 AI에서 사용
이 디렉터리 전체를 ChatGPT, Codex, Claude 또는 Gemini의 파일 실행 환경에 제공한다.
Python 3와 Pillow, jsonschema를 설치한다. 작업 디렉터리의 SKILL.md를 읽고 scripts/pack.py, scripts/validate.py, scripts/preview.py를 사용한다.
APK는 패키지 실행기이며 테마 제작에는 Android 빌드가 필요 없다.
결과물은 .mwidget, 미리보기 PNG, 사용한 에셋의 라이선스다. 코드 실행이 불가능한 환경에서는 검증·패키징 완료를 주장하지 않는다.
