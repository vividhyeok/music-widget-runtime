# Music Widget 제작 키트
이 ZIP과 함께 받은 사용자의 요구사항을 바탕으로 설치 가능한 음악 위젯 테마를 제작한다.
사용자가 별도 맥락을 제공하지 않아도 아래 내용을 프로젝트 맥락으로 사용한다.

## 먼저 할 일
1. SKILL.md와 references/runtime.md, references/schema.json을 읽는다.
2. 사용자 메시지와 첨부 레퍼런스에서 스타일, 필요한 정보와 버튼, 비율을 정한다. 불필요한 기능을 추가하지 않는다.
3. examples의 테마를 구조 참고용으로 사용하고 새 디렉터리에 제작한다.
4. 아래 명령으로 검증과 미리보기를 마친 실제 파일을 전달한다.

## 프로젝트 맥락
휴대폰에는 Android Music Widget Runtime APK가 설치되어 있다. APK가 .mwidget 파일을 읽고 음악 앱의 곡 정보와 제어 기능을 연결한다.
이번 작업의 결과물은 별도 .mwidget 테마와 PNG 미리보기다. 테마 변경에 APK 빌드는 필요 없다.
사용자는 레퍼런스를 최대한 닮은 디자인을 선호한다. 비율은 레퍼런스로 결정하고, 요청한 기능만 디자인에 배치한다.
사용자의 새 요구사항이 이 키트의 디자인 기본값보다 우선한다.

## 실행
ZIP을 풀고 이 README.md가 있는 폴더에서 실행한다.
Python 3.10 이상과 파일 실행 환경이 필요하다.

```sh
python -m pip install -r requirements.txt
python scripts/pack.py examples/nocturne output/example.mwidget
python scripts/validate.py output/example.mwidget
python scripts/preview.py output/example.mwidget output/example.png
python scripts/preview.py output/example.mwidget output/example-paused.png --paused --title "긴 제목과 한국어 표시 확인"
```

실제 제작은 examples를 새 테마 폴더로 복사하고 새 id와 name을 지정해 시작한다.
미리보기 PNG를 열어 글자 잘림, 버튼 겹침, 여백을 점검한 뒤 수정한다.
출력 파일은 테마 원본 디렉터리 밖에 저장한다.
완료 응답에는 다운로드 가능한 .mwidget과 미리보기 PNG를 첨부하고 선택한 기능과 비율을 짧게 설명한다.
설치 방법은 앱에서 .mwidget 가져오기 후 대상 위젯에 적용하기다.
파일 실행이나 첨부파일 읽기를 지원하지 않는 환경에서는 실제 제작 완료를 주장하지 말고 필요한 실행 환경을 안내한다.
