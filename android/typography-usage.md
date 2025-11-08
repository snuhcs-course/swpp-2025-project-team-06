# Typography 스타일 사용 현황

Type.kt에 정의된 각 Typography 스타일들의 사용 위치를 정리한 문서입니다.

## ✅ 사용된 Typography 스타일들

### Display Styles (큰 제목)
- **`displayLarge`** (32sp, Bold)
  - `RegisterScreen.kt`: `displayLarge` - 회원가입 화면 타이틀 (2곳)
  - `CommonTopBar.kt`: `displayLarge` - 상단 바 타이틀
  - `SearchLoadingState.kt`: `displayLarge.copy(fontSize = 80.sp)` - 로딩 화면 이모지 아래 텍스트
  - `LoginScreen.kt`: `displayLarge` - 로그인 화면 타이틀 (2곳)

- **`displayMedium`** (28sp, Bold)
  - `LocalAlbumScreen.kt`: `displayMedium` - 로컬 앨범 화면 타이틀
  - `AlbumScreen.kt`: `displayMedium.copy(` - 앨범 화면 타이틀
  - `LocalGalleryScreen.kt`: `displayMedium` - 로컬 갤러리 화면 타이틀

- **`displaySmall`** (24sp, SemiBold)
  - `ErrorDialog.kt`: `displaySmall` - 에러 다이얼로그 타이틀

### Headline Styles (제목)
- **`headlineLarge`** (21sp, SemiBold)
  - `SelectImageScreen.kt`: `headlineLarge` - 이미지 선택 화면 타이틀 (2곳)
  - `AddTagScreen.kt`: `headlineLarge` - 태그 추가 화면 타이틀 및 입력 필드 (5곳)

- **`headlineMedium`** (20sp, SemiBold)
  - `ErrorDialog.kt`: `headlineMedium` - 에러 다이얼로그 제목 (2곳)
  - `StoryScreen.kt`: `headlineMedium` - 스토리 화면 제목 (2곳)
  - `RegisterScreen.kt`: `headlineMedium` - 회원가입 버튼 텍스트

- **`headlineSmall`** (18sp, Medium)
  - `SearchResultScreen.kt`: `headlineSmall` - 검색 결과 화면 제목
  - `SearchLoadingState.kt`: `headlineSmall.copy(fontWeight = FontWeight.Medium)` - 로딩 상태 텍스트
  - `LoginScreen.kt`: `headlineSmall` - 로그인 버튼 텍스트

### Body Styles (본문)
- **`bodyMedium`** (14sp, Normal)
  - `HomeScreen.kt`: `bodyMedium` - 홈 화면 본문
  - `WarningBanner.kt`: `bodyMedium` 및 `bodyMedium.copy(fontWeight = FontWeight.Medium)` - 경고 배너 텍스트 (2곳)
  - `ErrorDialog.kt`: `bodyMedium` - 에러 메시지 (2곳)
  - `Tag.kt`: `bodyMedium` - 태그 텍스트
  - `StoryScreen.kt`: `bodyMedium.copy(fontWeight = FontWeight.Medium)` - 스토리 본문 (2곳)
  - `SearchResultScreen.kt`: `bodyMedium` - 검색 결과 설명 (2곳)
  - `AlbumScreen.kt`: `bodyMedium` - 앨범 설명

- **`bodySmall`** (12sp, Normal)
  - `RegisterScreen.kt`: `bodySmall` - 회원가입 폼 라벨 및 설명 (6곳)
  - `StoryScreen.kt`: `bodySmall` - 스토리 부가 정보 (3곳)
  - `LoginScreen.kt`: `bodySmall` - 로그인 폼 라벨 및 설명 (3곳)
  - `LocalGalleryScreen.kt`: `bodySmall` - 로컬 갤러리 설명
  - `HomeScreen.kt`: `bodySmall` - 홈 화면 부가 정보

### Label Styles (라벨/버튼)
- **`labelLarge`** (16sp, Bold)
  - `CreateTagButton.kt`: `labelLarge` - 태그 생성 버튼 텍스트
  - `ErrorDialog.kt`: `labelLarge` - 에러 다이얼로그 버튼 (2곳)
  - `StoryScreen.kt`: `labelLarge` - 스토리 액션 버튼
  - `AlbumScreen.kt`: `labelLarge` - 앨범 액션 버튼

- **`labelMedium`** (14sp, SemiBold)
  - `AlbumScreen.kt`: `labelMedium.copy(fontWeight = FontWeight.Bold)` - 앨범 라벨

- **`labelSmall`** (12sp, Medium)
  - `BottomNavBar.kt`: `labelSmall.copy(` - 하단 네비게이션 라벨

## ❌ 사용하지 않아 제거한 Typography 스타일들
Type.kt에 정의되지 않은 Material3 기본 스타일들:
- `titleLarge` (16sp, SemiBold)
- `titleMedium` (16sp, Medium)
- `titleSmall` (14sp, Medium)
- `bodyLarge` (16sp, Normal)

---

**요약**: 총 11개 스타일이 Type.kt에 정의되어 있으며, 모두 사용되고 있습니다. (Material3의 15개 기본 스타일 중 4개를 제외함)