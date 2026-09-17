# 젠더리빌 와이어프레임

[Requirements.md](../Requirements.md) 기준으로 만든 저충실도 와이어프레임입니다.

**미리보기(라이브 링크)**: https://claude.ai/artifact/FnyMrMcbQ8mYLnyHVRfoRV

`project/`의 `.dc.html` 파일들은 Claude 아티팩트(Design Canvas) 전용 포맷이라 이 저장소만으로는 렌더링되지 않으며,
위 라이브 링크에서만 실제로 확인/편집할 수 있습니다. 이 폴더는 그 소스를 버전 관리 목적으로 보관하는 사본입니다.

## 화면 구성 (9개)

| 파일 | 화면 |
|---|---|
| `Main.dc.html` | ① 인트로 |
| `Secret.dc.html` | 비밀 상태 (공개 예정 이전) |
| `Select.dc.html` | ② 선택 (성별 맞추기) |
| `Result_Box.dc.html` | ③ 결과 확인 · 서프라이즈 박스 테마 |
| `Result_Cake.dc.html` | 결과 확인 테마 변형 · 케이크 |
| `Result_Balloon.dc.html` | 결과 확인 테마 변형 · 풍선 |
| `Guestbook.dc.html` | ④ 축하글 남기기 |
| `Expired.dc.html` | 만료 안내 (보관주기 종료) |
| `Admin.dc.html` | 관리자 대시보드 (데스크톱) |

인트로 → 선택 → 결과 확인(서프라이즈 박스) 3개 화면은 실제 클릭 시 다음 화면으로 이동하는 프로토타입 링크로 연결되어 있습니다.
