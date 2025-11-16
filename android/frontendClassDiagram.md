```mermaid
classDiagram
    direction TB
    class MainActivity {
            +onCreate(savedInstanceState)
        }
        class Navigation {
            <<Composable>>
            +NavHostController navController
            +setupNavGraph()
        }
    namespace View {
        class HomeScreen {
            <<Composable>>
            +navController NavController
            +homeViewModel HomeViewModel
            +photoViewModel PhotoViewModel
            +render()
        }
        class LoginScreen {
            <<Composable>>
            +navController NavController
            +authViewModel AuthViewModel
            +render()
        }
        class RegisterScreen {
            <<Composable>>
            +navController NavController
            +authViewModel AuthViewModel
            +render()
        }
        class AlbumScreen {
            <<Composable>>
            +tagName String
            +navController NavController
            +albumViewModel AlbumViewModel
            +render()
        }
        class LocalGalleryScreen {
            <<Composable>>
            +navController NavController
            +localViewModel LocalViewModel
            +render()
        }
        class LocalAlbumScreen {
            <<Composable>>
            +albumId Long
            +albumName String
            +navController NavController
            +localViewModel LocalViewModel
            +render()
        }
        class ImageDetailScreen {
            <<Composable>>
            +photoPathId Long
            +navController NavController
            +imageDetailViewModel ImageDetailViewModel
            +render()
        }
        class SearchResultScreen {
            <<Composable>>
            +initialQuery String
            +navController NavController
            +searchViewModel SearchViewModel
            +render()
        }
        class AddTagScreen {
            <<Composable>>
            +navController NavController
            +addTagViewModel AddTagViewModel
            +render()
        }
        class SelectImageScreen {
            <<Composable>>
            +navController NavController
            +selectImageViewModel SelectImageViewModel
            +render()
        }
        class StoryScreen {
            <<Composable>>
            +navController NavController
            +storyViewModel StoryViewModel
            +render()
        }
        class MyTagsScreen {
            <<Composable>>
            +navController NavController
            +myTagsViewModel MyTagsViewModel
            +render()
        }
    }
    namespace ViewModel {
        class AuthViewModel {
            -TokenRepository tokenRepository
            +isLoggedIn String?
            +isSessionLoaded Boolean
            +loginState LoginState
            +registerState RegisterState
            +refreshState RefreshState
            +logoutState LogoutState
            +login(username, password)
            +register(email, username, password)
            +refreshTokens()
            +logout()
            +resetLoginState()
            +resetRegisterState()
            +resetRefreshState()
            +resetLogoutState()
        }
        class HomeViewModel {
            -PhotoSelectionRepository photoSelectionRepository
            -LocalRepository localRepository
            -RemoteRepository remoteRepository
            -TokenRepository tokenRepository
            +homeLoadingState HomeLoadingState
            +homeDeleteState HomeDeleteState
            +allTags List~TagItem~
            +selectedPhotos List~Photo~
            +isLoadingPhotos Boolean
            +isLoadingMorePhotos Boolean
            +showAllPhotos Boolean
            +isSelectionMode Boolean
            +groupedPhotos List~DatedPhotoGroup~
            +allPhotos List~Photo~
            +loadServerTags()
            +loadAllPhotos()
            +deleteTag(tagId)
            +togglePhoto(photo)
            +setSelectionMode(enabled)
            +setShowAllPhotos(show)
        }
        class PhotoViewModel {
            -RemoteRepository remoteRepository
            -LocalRepository localRepository
            +uploadPhotos(photos)
        }
        class AlbumViewModel {
            -RemoteRepository remoteRepository
            +photoByTag List~Photo~
            +getPhotoByTag(tagName)
        }
        class LocalViewModel {
            -LocalRepository localRepository
            -ImageBrowserRepository imageBrowserRepository
            +images List~Uri~
            +albums List~Album~
            +imagesInAlbum List~Photo~
            +selectedPhotosInAlbum Set~Photo~
            +getImages()
            +getAlbums()
            +getImagesForAlbum(albumId)
            +togglePhotoSelection(photo)
            +clearPhotoSelection()
        }
        class SearchViewModel {
            -SearchRepository searchRepository
            -PhotoSelectionRepository photoSelectionRepository
            -LocalRepository localRepository
            -ImageBrowserRepository imageBrowserRepository
            -TokenRepository tokenRepository
            -RemoteRepository remoteRepository
            +tagLoadingState TagLoadingState
            +searchState SemanticSearchState
            +allTags List~TagItem~
            +isSelectionMode Boolean
            +selectedPhotos List~Photo~
            +searchHistory List~String~
            +search(query, offset)
            +resetSearchState()
            +loadServerTags()
            +togglePhoto(photo)
            +setSelectionMode(enabled)
        }
        class ImageDetailViewModel {
            -ImageBrowserRepository imageBrowserRepository
            +imageUri Uri?
            +imageContext ImageContext?
            +loadImage(photoPathId)
            +setImageContext(context)
            +clearImageContext()
            +setSingleImage(uri)
        }
        class AddTagViewModel {
            -PhotoSelectionRepository photoSelectionRepository
            -LocalRepository localRepository
            -RemoteRepository remoteRepository
            +tagName String
            +selectedPhotos List~Photo~
            +existingTags List~String~
            +isTagNameDuplicate Boolean
            +saveState SaveState
            +updateTagName(name)
            +addPhoto(photo)
            +removePhoto(photo)
            +saveTagAndPhotos()
            +clearDraft()
        }
        class SelectImageViewModel {
            -PhotoSelectionRepository photoSelectionRepository
            -LocalRepository localRepository
            -RemoteRepository remoteRepository
            -ImageBrowserRepository imageBrowserRepository
            -RecommendRepository recommendRepository
            +tagName String
            +selectedPhotos List~Photo~
            +existingTagId String?
            +allPhotos List~Photo~
            +isLoading Boolean
            +isLoadingMore Boolean
            +recommendState RecommendState
            +isSelectionMode Boolean
            +recommendedPhotos List~Photo~
            +addPhotosState AddPhotosState
            +getAllPhotos()
            +loadMorePhotos()
            +togglePhoto(photo)
            +getRecommendedPhotos()
            +addPhotosToExistingTag()
        }
        class StoryViewModel {
            -RemoteRepository remoteRepository
            +storyState StoryState
            +getStoryByTag(tagId)
            +likeStory(storyId)
        }
        class MyTagsViewModel {
            -RemoteRepository remoteRepository
            -PhotoSelectionRepository photoSelectionRepository
            +uiState MyTagsUiState
            +isEditMode Boolean
            +selectedTagsForBulkEdit Set~String~
            +sortOrder TagSortOrder
            +tagActionState TagActionState
            +selectedPhotos List~Photo~
            +saveState SaveState
            +loadTags()
            +toggleEditMode()
            +toggleTagSelection(tagId)
            +deleteSelectedTags()
            +updateTagName(tagId, newName)
            +setSortOrder(order)
        }
    }
    namespace Model {
        class Tag {
            +String tagId
            +String tagName
        }
        class Photo {
            +String photoId
            +Uri contentUri
        }
        class Album {
            +Long albumId
            +String albumName
            +Uri thumbnailUri
        }
        class PhotoMeta {
            +String filename
            +Int photo_path_id
            +String created_at
            +Double lat
            +Double lng
        }
        class ImageContext {
            +List~Uri~ images
            +Int currentIndex
            +ContextType contextType
        }
        class StoryModel {
            +String storyId
            +String title
            +List~Photo~ photos
        }
        class SessionStore {
            <<interface>>
            +accessTokenFlow String?
            +refreshTokenFlow String?
            +isLoaded Boolean
            +getAccessToken() String?
            +getRefreshToken() String?
            +saveTokens(accessToken, refreshToken)
            +clearTokens()
        }
        class SessionManager {
            -DataStore~Preferences~ dataStore
            +getInstance(context) SessionManager
            +getAccessToken() String?
            +getRefreshToken() String?
            +saveTokens(accessToken, refreshToken)
            +clearTokens()
        }
        class ApiService {
            <<interface>>
            +getHomeTags() List~Tag~
            +getPhotosByTag(tagName) List~Photo~
            +login(loginRequest) Response~LoginResponse~
            +register(registerRequest) Response~RegisterResponse~
            +refreshToken(refreshRequest) Response~RefreshResponse~
            +logout(logoutRequest) Response~Unit~
            +uploadPhotos(photo, metadata) Response~Unit~
            +semanticSearch(query, offset) Response~SemanticSearchResponse~
            +getRecommendedPhotos(tagId) Response~List~Photo~~
            +saveTag(tagRequest) Response~Unit~
            +getStoryByTag(tagId) Response~StoryModel~
        }
        class RetrofitInstance {
            -String BASE_URL
            -ApiService? apiService
            +getApiService(context) ApiService
        }
        class AuthInterceptor {
            -SessionStore sessionStore
            +intercept(chain) Response
        }
        class TokenAuthenticator {
            -SessionStore sessionStore
            +authenticate(route, response) Request?
            -refreshTokenApi(refreshToken) Response~RefreshResponse~
        }
        class RemoteRepository {
            -ApiService apiService
            +getAllTags() List~Tag~
            +getPhotosByTag(tagName) List~Photo~
            +uploadPhotos(photoUploadData) Response~Unit~
            +saveTag(tagRequest) Response~Unit~
            +getAllPhotos() List~Photo~
        }
        class LocalRepository {
            -Context context
            +getImages() List~Uri~
            +getPhotoUploadRequest() PhotoUploadData
            +getAlbums() List~Album~
            +getImagesForAlbum(albumId) List~Uri~
        }
        class TokenRepository {
            -ApiService apiService
            -SessionStore sessionStore
            +isLoggedIn String?
            +isSessionLoaded Boolean
            +login(username, password) LoginResult
            +register(email, username, password) RegisterResult
            +refreshTokens() RefreshResult
            +logout()
        }
        class SearchRepository {
            -ApiService apiService
            +semanticSearch(query, offset) SearchResult
        }
        class PhotoSelectionRepository {
            +tagName String
            +selectedPhotos List~Photo~
            +existingTagId String?
            +initialize(tagName, photos, existingTagId)
            +updateTagName(name)
            +addPhoto(photo)
            +removePhoto(photo)
            +togglePhoto(photo)
            +clear()
            +hasChanges() Boolean
        }
        class RecommendRepository {
            -ApiService apiService
            +getRecommendedPhotos(tagId) List~Photo~
        }
        class ImageBrowserRepository {
            -LocalRepository localRepository
            +getImageUri(photoPathId) Uri?
            +getImageContext() ImageContext?
        }
    }
    SessionStore <|.. SessionManager
    LocalRepository ..> PhotoMeta : creates
    RetrofitInstance ..> ApiService : creates
    RetrofitInstance ..> AuthInterceptor : uses
    RetrofitInstance ..> TokenAuthenticator : uses
    AuthInterceptor --> SessionStore : uses
    TokenAuthenticator --> SessionStore : uses
    TokenAuthenticator ..> ApiService : uses for refresh
    RemoteRepository --> ApiService : uses
    TokenRepository --> ApiService : uses
    TokenRepository --> SessionStore : uses
    SearchRepository --> ApiService : uses
    RecommendRepository --> ApiService : uses
    PhotoSelectionRepository ..> Photo : manages
    ImageBrowserRepository --> LocalRepository : uses
    AuthViewModel --> TokenRepository : uses
    HomeViewModel --> PhotoSelectionRepository : uses
    HomeViewModel --> LocalRepository : uses
    HomeViewModel --> RemoteRepository : uses
    HomeViewModel --> TokenRepository : uses
    AlbumViewModel --> RemoteRepository : uses
    LocalViewModel --> LocalRepository : uses
    LocalViewModel --> ImageBrowserRepository : uses
    LocalViewModel ..> Album : manages
    SearchViewModel --> SearchRepository : uses
    SearchViewModel --> PhotoSelectionRepository : uses
    SearchViewModel --> LocalRepository : uses
    SearchViewModel --> ImageBrowserRepository : uses
    SearchViewModel --> TokenRepository : uses
    SearchViewModel --> RemoteRepository : uses
    ImageDetailViewModel --> ImageBrowserRepository : uses
    ImageDetailViewModel ..> ImageContext : manages
    AddTagViewModel --> PhotoSelectionRepository : uses
    AddTagViewModel --> LocalRepository : uses
    AddTagViewModel --> RemoteRepository : uses
    SelectImageViewModel --> PhotoSelectionRepository : uses
    SelectImageViewModel --> LocalRepository : uses
    SelectImageViewModel --> RemoteRepository : uses
    SelectImageViewModel --> ImageBrowserRepository : uses
    SelectImageViewModel --> RecommendRepository : uses
    StoryViewModel --> RemoteRepository : uses
    MyTagsViewModel --> RemoteRepository : uses
    MyTagsViewModel --> PhotoSelectionRepository : uses
    MainActivity ..> Navigation : creates
    Navigation --> HomeScreen : routes to
    Navigation --> LoginScreen : routes to
    Navigation --> RegisterScreen : routes to
    Navigation --> AlbumScreen : routes to
    Navigation --> LocalGalleryScreen : routes to
    Navigation --> LocalAlbumScreen : routes to
    Navigation --> ImageDetailScreen : routes to
    Navigation --> SearchResultScreen : routes to
    Navigation --> AddTagScreen : routes to
    Navigation --> SelectImageScreen : routes to
    Navigation --> StoryScreen : routes to
    Navigation --> MyTagsScreen : routes to
    HomeScreen --> HomeViewModel : observes
    HomeScreen --> PhotoViewModel : observes
    HomeScreen --> SearchViewModel : observes
    HomeScreen --> AuthViewModel : observes
    LoginScreen --> AuthViewModel : observes
    RegisterScreen --> AuthViewModel : observes
    AlbumScreen --> AlbumViewModel : observes
    LocalGalleryScreen --> LocalViewModel : observes
    LocalAlbumScreen --> LocalViewModel : observes
    ImageDetailScreen --> ImageDetailViewModel : observes
    SearchResultScreen --> SearchViewModel : observes
    AddTagScreen --> AddTagViewModel : observes
    SelectImageScreen --> SelectImageViewModel : observes
    StoryScreen --> StoryViewModel : observes
    MyTagsScreen --> MyTagsViewModel : observes
    ```