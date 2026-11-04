# 런타임 지원 범위
- 캔버스 너비 240~1024, 높이 120~1024의 정수. 정사각형과 세로형도 가능하다.
- 실제 위젯 안에서 원본 비율을 유지해 맞추고 남는 영역은 투명하다. 화면 크기에 따라 요소가 재배치되지는 않는다. 모든 요소는 캔버스 안에 있어야 한다.
- layers는 1~32개, touch는 0~8개. 지원 기능을 전부 넣을 필요가 없다.
- image는 src PNG, artwork는 fallback PNG와 동적 앨범아트, icon은 action과 src PNG를 사용한다.
- text의 field는 title, artist, album, player, status, elapsed, duration. font는 패키지 내 TTF/OTF다.
- progress는 color와 trackColor로 재생 진행 정도를 표시한다. 탐색 동작이 없다.
- touch의 action은 previous, toggle, next, switch, settings, rewind, forward, stop이다. toggle의 icon에는 playingSrc로 일시정지 PNG를 지정한다.
- rewind/forward는 10초 이동, stop은 정지. 음악 앱의 지원 여부에 따라 실행되지 않을 수 있다.
- 진행 시간은 재생 중 약 5초 간격으로 갱신한다. 길이를 알 수 없으면 시간은 —, 진행 막대는 빈 트랙이다.
- 셔플, 반복, 가사, 음량, 임의 위치 탐색, 커스텀 앱 실행, 애니메이션은 현재 규격에 없다. 지원하지 않는 필드를 만들어 넣지 않는다.
- 배경 터치는 선택된 음악 앱 열기에 연결된다. settings 버튼은 선택 사항이며 앱에서도 설정할 수 있다.
- 텍스트는 한 줄 말줄임이다. 미리보기의 시스템 대체 폰트가 글리프 누락을 숨길 수 있으므로 한글을 지원하는 포함 폰트를 확인한다.
- 예제 폰트를 재사용할 때 예제 LICENSE.txt도 유지한다. 새 폰트는 눈누에서 우선 탐색하고 원저작자의 재배포 조건을 확인한다.
- 최종 스키마와 검증 도구를 기준으로 작성하며, PC 미리보기와 실제 Android 렌더링은 다를 수 있다.


## 커스텀 진행 슬라이더
progress에 trackSrc, fillSrc, thumbSrc PNG를 선택적으로 지정한다.
thumbSrc와 thumbWidth는 함께 지정하고 thumbWidth는 레이어 w 이하로 한다.
손잡이는 레이어 높이 h에 맞춰 그려지며 레이어 내부에서 왼쪽부터 오른쪽까지 움직인다.
얇은 레일과 큰 손잡이는 레일 PNG에 투명한 상하 여백을 넣어 만든다.
showFill=false로 채움을 숨기면 클래식 슬라이더가 된다. radius=0은 각진 기본 도형이다.
길이가 없는 곡은 레일만 표시한다. 손잡이는 재생 위치를 표시하며 터치 탐색이나 드래그를 제공하지 않는다.
이 옵션은 v0.2.0 이후 런타임 업데이트가 필요하다. 이전 APK에는 새 필드를 넣은 테마를 가져올 수 없다.
