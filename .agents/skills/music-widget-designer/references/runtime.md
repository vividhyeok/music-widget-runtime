# 런타임 규격 / 0.3.0

새 테마는 version=2, 기존 v1 테마는 그대로 실행한다. version은 테마 규격이며 APK 버전과 다르다. 전체 허용 키와 범위는 schema.json, 실행 가능 여부는 capabilities.json을 기준으로 한다. 현재 APK에서 지원하지 않는 효과를 추측해서 넣지 않는다.

## 공통 좌표와 그룹
논리 캔버스 너비 240~1024, 높이 120~1024. 비율은 자유롭고 실제 위젯에 비율을 유지해 맞춘다. 모든 레이어는 type,x,y,w,h를 갖고 순서대로 합성한다. 루트 좌표는 캔버스, group.children 좌표는 부모의 로컬 좌표다. 총 64노드, 그룹 중첩 4단계, touch 8개까지다. 캔버스 밖으로 변형된 그림은 잘리므로 미리보기에서 검사한다. v1은 최대 32개와 캔버스 내부 사각형 제한을 유지한다.

공통 선택 속성: id, opacity(0~1), visibleWhen(always/playing/paused/hasArtwork/noArtwork), transform, mask(none/circle/rounded), radius, maskSrc(PNG 알파), animation. opacity는 그룹 전체에 한 번 적용된다. mask는 로컬 사각형 기준이며 그룹에도 적용된다. 반투명 배경은 PNG 알파 또는 레이어 opacity로 만든다.

transform은 quad → skew → scale → rotation → x,y 이동 순이다. rotation은 도, scaleX/Y 기본 1, skewX/Y는 기울기 계수, pivotX/Y는 0~1(기본 .5)이다. quad는 로컬 사각형의 좌상·우상·우하·좌하를 순서대로 옮길 8개 정규화 값이다. 0~1 범위의 시계방향 볼록 사각형만 허용한다. 원근이 필요 없는 단순 기울임에는 rotation을 쓴다.

## 레이어
- image: src PNG. artwork: 동적 앨범아트와 fallback PNG. icon: action/src, toggle에는 playingSrc. fit은 crop/contain/stretch이며 artwork 기본 crop, 나머지 stretch다.
- group: children 배열. 변형·마스크·투명도를 묶어서 적용한다.
- shape: shape=rect/ellipse/polygon, color. radius, strokeColor/strokeWidth, gradient={endColor,angle} 선택. polygon은 정규화 points=[x,y,...] 3~64꼭짓점을 사용한다. 질감·복잡한 벡터·그림자·블러는 제작 시 PNG로 만들어 image로 둔다.
- text: field=title/artist/album/player/status/elapsed/duration/literal. literal은 text도 필요하다. font는 포함 TTF/OTF, size, color 필수. align=left/center/right, valign=top/center/bottom, overflow=ellipsis/clip/wrap/marquee. wrap은 maxLines(기본3,최대8), lineSpacing 배수(기본1). letterSpacing은 em 단위다. 기본은 좌상단 한 줄 말줄임. 제목에만 필요한 경우 marquee, 아티스트는 ellipsis를 권장한다. 메타데이터를 배경 PNG에 굽지 않는다.
- progress: color/trackColor 필수, radius, showFill, trackSrc/fillSrc, thumbSrc/thumbWidth 선택. thumbSrc와 thumbWidth는 쌍이며 레이어 너비 이하. direction=horizontal/vertical/circular. 수직은 아래부터 위, 원형은 strokeWidth/startAngle(기본-90)/sweepAngle(기본360). 원형에는 커스텀 트랙/손잡이 PNG를 사용하지 않는다. 재생 길이 미상은 빈 트랙이다. 드래그·임의 탐색을 제공하지 않는다.
- equalizer: mode=static/simulated, color, bars(3~48), gap(0~1), seed 선택. 실제 오디오 분석이 아니다. simulated는 재생 중 정해진 수학 패턴을 단계별 표시한다.

## 상태와 단계별 움직임 (실험)
animation={kind,durationMs,steps,when,fallback}. kind는 rotate/translate/pulse/frames, durationMs=1000~60000, steps=2~12, when=playing/always, fallback="static" 필수다. rotate from/to 기본 0/360도, translate dx/dy는 왕복 이동, pulse from/to는 0~1 투명도, frames는 sources PNG 배열 길이를 steps와 맞춘다. frames는 image/artwork/icon에만 사용한다.

호스트는 정적 구간을 한 번 합성하고 움직이는 루트별로 알파 영역만 잘라 ViewFlipper에 전달한다. 프레임마다 서비스를 깨우거나 전체 위젯을 재전송하지 않는다. 루트 최대 3개, 실제 간격 최소 500ms, 프레임 최대 12개다. 모든 활성 효과는 같은 durationMs를 사용한다. marquee와 simulated가 있으면 12000ms로 맞춘다. 다른 주기는 정적 fallback으로 간다. steps는 샘플 수 상한이며 다른 효과와 함께 쓰면 공통 샘플 타임라인을 사용한다. 이것은 부드러운 연속 회전이나 실시간 마키를 보장하는 기능이 아니다.

반응형 크기 전체에서 합계 900000 bitmap 픽셀 예산을 초과하면 해상도를 무리하게 낮추지 않고 정적 합성으로 바꾼다. 사용자 앱의 움직임 스위치, 절전 모드, 시스템 애니메이션 비활성화도 정적으로 전환한다. 정적 fallback에서 rotate/translate/pulse는 기본 정지 자세, frames는 src/fallback/playingSrc, marquee는 말줄임, equalizer는 고정 패턴이다. 프로세스나 런처가 다시 적용하면 움직임 위상이 재시작할 수 있다. 상태 변화와 시간 표시 갱신은 MediaSession과 기존 약 5초 갱신 정책을 사용하며, 시간 표시가 없는 테마는 주기 갱신을 하지 않는다.

## 터치
기존 {x,y,w,h,action,label} 또는 v2 {target:노드id,action,label}. target은 부모 변형까지 계산한 축정렬 경계 사각형이다. 정밀 다각형 터치는 아니다. 노드나 조상에 animation/조건부 표시가 있으면 target을 거부하므로 고정 컨트롤 영역을 따로 둔다. 서로 겹치거나 캔버스 밖인 터치는 거부한다. 48dp 이상을 목표로 최종 크기에서 검사한다. previous/toggle/next/switch/settings/rewind/forward/stop 중 선택한다. rewind/forward는10초이며 플레이어가 지원할 때만 실행한다. 배경은 선택된 음악 앱을 연다.

## 폰트와 파일
v2의 각 text.font는 fonts 배열에 {file,noonnu,source,license:"LICENSE.txt"} 출처를 한 번 선언한다. noonnu는 https://noonnu.cc/font_page/숫자 상세 주소, source는 실제 원 배포처 https 주소다. 라이선스의 법적 허용 여부는 AI가 읽고 판단하며 검증기는 출처 구조와 고지 파일 존재를 검사한다. 출처 필드가 있다고 허용을 인증하는 것은 아니다.

ZIP 16MiB, 해제24MiB,64파일,파일8MiB,압축비200, PNG 한 변2048/2097152픽셀/합계8388608픽셀,폰트 v1=2MiB/v2=8MiB. manifest128KiB. PNG·TTF/OTF·manifest.json·LICENSE.txt만 허용한다. fonts 출처 URL은 메타데이터이며 앱이 네트워크로 다운로드하지 않는다. 라이선스는 LICENSE.txt 하나에 모두 모은다. v1의 예제 폰트는 과거 형식 호환용이며 새 테마에는 눈누 폰트 선택 규칙을 따른다.
