# 12지신 및 아기 캐릭터 통합 디자인 시스템 가이드라인 (Unified Design System Guidelines)

## 1. 개요 및 디자인 철학 (Overview & Design Philosophy)
본 가이드라인은 브랜드 및 서비스 디자인 시스템에서 사용할 12지신 동물 캐릭터 12종 및 아기 캐릭터(남자아기, 여자아기) 2종의 시각적 일관성과 완성도를 유지하기 위해 작성되었습니다.

* **디자인 핵심 키워드:** Minimalist, Friendly, Flat 2D Vector, Bold Outlines, Symmetrical Balance.
* **디자인 목표:** 귀, 머리칼, 악세서리 등 신체적 부속물의 비대칭/누락 현상을 방지하고, 14종 전 캐릭터가 하나의 통일된 브랜드 자산(Asset) 세트로 느껴지도록 규칙적 요소를 강제함.

---

## 2. 캐릭터 통합 마스터 규격 (Master Specifications)

### 2.1 형태 및 구도 (Form & Composition)
1. **아이콘 구도:** 
   * **12지신:** 몸통이 없는 '얼굴 전면 머리 아이콘(Head-only Icon)' 형태.
   * **아기 캐릭터:** 상반신 또는 머리 중심 프레이밍 (손동작 표현 가능, 라인 두께 통일).
2. **외곽선 (Stroke):** 굵고 두꺼운 검은색 단색 라인 아트 (`#1A1A1A` 또는 `#000000`, 약 3~4pt 두께 유지). 라인에 그라데이션이나 질감 표현 금지.
3. **대칭성 (Symmetry):** 모든 얼굴형, 귀, 볼, 머리칼 분할, 악세서리는 중앙축을 기준으로 **좌우 대칭(Horizontal Symmetry)** 구조를 기본 원칙으로 준수.
4. **얼굴 이목구비 표준 (Facial Features Standards):**
   * **눈 (Eyes):** 단순한 검은색 정원 형태의 점(Dot eyes) 또는 아래가 둥근 형태. 수평 정렬 필수.
   * **입/코 (Mouth/Nose):** 작은 곡선 코 점, 웃는 U자형 입 (내부는 밝은 핑크/적색 단색 채움).
   * **볼 터치 (Cheeks):** 원형의 밝은 파스텔 핑크 단색 블러시 필수 포함.

### 2.2 색상 및 팔레트 (Color Palette)
* **피부 톤:** 밝은 아이보리/베이지 단색 채움 (`#FFF3E0` 또는 `#FFEBE0`).
* **의상 및 악세서리:** 파스텔 톤 단색 필 (소프트 핑크 `#FFB6C1`, 스카이 블루 `#87CEEB`).
* **음영 금지:** 입체감을 위한 그림자, 하이라이트, 그라데이션, 3D 렌더링 스타일 엄격히 금지.
* **배경:** 투명 배경 또는 순수 흰색 배경 (`#FFFFFF`).

---

## 3. 신규 아기 캐릭터 상세 규격 (Baby Character Specifications)

### 1) 여자아기 (Baby Girl)
* **주요 색상:** 갈색(머리카락), 핑크색(헤어밴드/리본/의상), 베이지(피부)
* **핵심 구조 정의:**
  * **머리카락:** 정갈하게 갈라진 앞머리와 양옆으로 땋은 양갈래 머리(Pigtails).
  * **악세서리:** 머리 상단 중앙에 위치한 땡땡이(Polka dot) 패턴의 핑크색 리본 헤어밴드.
  * **귀:** 머리카락 사이로 노출된 양쪽 대칭의 동그란 귀 (내부 귓바퀴 라인 표현).
  * **손동작:** 양손으로 턱을 괴고 있는 좌우 대칭 포즈.

### 2) 남자아기 (Baby Boy)
* **주요 색상:** 갈색(머리카락), 스카이 블루(의상), 베이지(피부)
* **핵심 구조 정의:**
  * **머리카락:** 단정하고 둥근 숏컷 스타일, 정수리에 살짝 솟은 귀여운 잔머리 삐침.
  * **의상:** 밝은 파란색과 흰색 스트라이프가 들어간 라운드 티셔츠.
  * **귀:** 양옆에 노출된 완벽한 대칭 구조의 동그란 귀.
  * **손동작:** 한 손을 들어 반갑게 인사하는 포즈 (또는 양손 대칭 프레이밍).

---

## 4. 프롬프트 표준 생성 템플릿 (Prompt Master Template)

### 아기 캐릭터 전용 프롬프트 템플릿

```text
A single, centered 2D vector line art illustration of a cute [GENDER] baby character, in the style of a clean minimalist design system mascot.
Bold black outlines, uniform stroke width, solid color fill without gradients, simple black dot eyes, bright pink blush cheeks, and a happy smiling expression.

[CHARACTER SPECIFIC FEATURES]
- Gender: [Baby Girl / Baby Boy]
- Hair Style: [Girl: Brown hair with symmetrical pigtails / Boy: Short brown hair with a small hair tuft on top]
- Accessories/Clothing: [Girl: Pink polka dot bow headband, pink top / Boy: Sky blue striped t-shirt]
- Ears: Symmetrical round ears visible on both sides with inner line details.
- Pose: [Girl: Resting chin on both hands / Boy: Waving one hand cheerfully]

Isolated on a plain solid white background (#FFFFFF).

[NEGATIVE CONSTRAINTS]
Full body, adult proportions, realistic skin textures, 3D render, complex shading, gradients, asymmetry, missing ears, extra fingers, distorted face, noise.
```

---

## 5. 문제 해결 및 오류 방지 체크리스트 (Troubleshooting Guide)

1. **귀나 양갈래 머리가 한쪽만 생성되는 경우:**
   * `symmetrical pigtails on both sides`, `two identical ears visible on left and right`를 프롬프트 상단에 배치합니다.
   * `asymmetrical hair, missing ear, single pigtail`을 네거티브 프롬프트에 추가합니다.
2. **리본 패턴이나 옷 주름이 너무 복잡해지는 경우:**
   * `simple flat polka dot pattern`, `minimalist flat vector clothes` 문구를 사용해 세부 디테일을 단순화합니다.
3. **12지신 동물 캐릭터와 비율이 안 맞는 경우:**
   * `chibi proportions`, `large round head`, `bold stroke matching design system` 키워드를 유지하여 선 두께와 얼굴 비율을 동기화합니다.
