# Music Widget 제작 키트 / 0.3.0

이 ZIP을 파일 실행이 가능한 ChatGPT 또는 Claude에 첨부하고 레퍼런스와 원하는 기능을 전달한다. 저장소 링크로 접근하는 AI는 루트 AGENTS.md/CLAUDE.md에서 같은 절차를 찾을 수 있다. 유료 구독 자체가 ZIP 접근·인터넷·Android 실행을 보장하지 않으므로 실제 제공되는 도구에 맞춰 검증 범위를 밝힌다. 별도 API 키는 필요 없다.

사용자용 요청 예:
> 첨부 레퍼런스의 구도와 질감을 살려 음악 위젯을 만들어줘. SKILL.md를 읽고 눈누에서 가장 어울리는 폰트를 골라줘. 필요한 기능은 네가 판단하고, 생성 이미지를 직접 확인해 고친 뒤 실제 .mwidget을 전달해줘.

AI는 SKILL.md, references/runtime.md, references/capabilities.json을 먼저 읽는다. examples/orbit는 눈누 SUITE 폰트와 v2 그룹·회전·마스크·상태·마키·이퀄라이저 예제다. 예제 디자인을 강제하지 않는다. 오프라인에서는 이 예제의 원본 폰트와 라이선스를 활용할 수 있다. 예전 nocturne/tape-deck는 v1 호환 참고용이다.

ZIP을 풀고 이 파일이 있는 곳에서:

```sh
python -m pip install -r requirements.txt
python scripts/build.py examples/orbit output/orbit
```

새 디자인은 예제를 별도 테마 폴더로 복사하고 id/name·레이어·에셋을 바꾼 뒤 같은 명령으로 제작한다. 출력 폴더는 원본 밖에 둔다. .mwidget, 상태별 approx PNG, contact-sheet.png, review.json이 생성된다. 오류를 고치고 이미지를 직접 열어 최대3회 검토·수정한다. 새 테마는 APK0.3.0 이상에서 실행한다. .mwidget을 가져오면 테마 교체에 APK 재설치는 필요 없다.

연결된 Android 검증 환경이 있으면 `--android-serial SERIAL`을 추가한다. 준비는 references/android-verification.md를 따른다. 일반 파일 실행 환경에서 Android 검증은 필수가 아닌 별도 실행 경로이며 실행하지 않았다면 report에 not-run으로 남긴다. Pillow 근사 PNG를 실제 Android 미리보기라고 전달하지 않는다.

완료 시 실제 .mwidget과 미리보기, 선택한 기능·눈누 폰트·검증 범위를 짧게 전달한다. 휴대폰 앱의 .mwidget 가져오기에서 적용한다. 사용자는 디자인 결정과 반복 점검 대신 레퍼런스 제공과 설치에 집중한다.
