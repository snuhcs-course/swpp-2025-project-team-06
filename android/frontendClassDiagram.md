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
    }
    namespace ViewModel {
        class AuthViewModel {
            -TokenRepository tokenRepository
            +StateFlow~String?~ isLoggedIn
            +StateFlow~Boolean~ isSessionLoaded
            +StateFlow~LoginState~ loginState
            +StateFlow~RegisterState~ registerState
            +StateFlow~RefreshState~ refreshState
            +StateFlow~LogoutState~ logoutState
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
            -RemoteRepository remoteRepository
            -LocalRepository localRepository
            -PhotoViewModel photoViewModel
            +StateFlow~HomeScreenUiState~ uiState
            +StateFlow~List~Tag~~ allTags
            +getAllTags()
            +uploadPhotos()
            +userMessageShown()
        }
        class PhotoViewModel {
            -RemoteRepository remoteRepository
            -LocalRepository localRepository
            +uploadPhotos(photos)
        }
        class AlbumViewModel {
            -RemoteRepository remoteRepository
            +StateFlow~List~Photo~~ photoByTag
            +getPhotoByTag(tagName)
        }
        class LocalViewModel {
            -LocalRepository localRepository
            +StateFlow~List~Uri~~ images
            +StateFlow~List~Album~~ albums
            +StateFlow~List~Uri~~ imagesInAlbum
            +getImages()
            +getAlbums()
            +getImagesForAlbum(albumId)
        }
        class SearchViewModel {
            -SearchRepository searchRepository
            +StateFlow~SemanticSearchState~ searchState
            +search(query, offset)
            +resetSearchState()
        }
        class ImageDetailViewModel {
            -ImageBrowserRepository imageBrowserRepository
            +StateFlow~Uri?~ imageUri
            +StateFlow~ImageContext?~ imageContext
            +loadImage(photoPathId)
            +setImageContext(context)
            +clearImageContext()
            +setSingleImage(uri)
        }
        class AddTagViewModel {
            -DraftTagRepository draftTagRepository
            -RecommendRepository recommendRepository
            -RemoteRepository remoteRepository
            +StateFlow~String~ tagName
            +StateFlow~List~Photo~~ selectedPhotos
            +StateFlow~RecommendState~ recommendState
            +StateFlow~SaveState~ saveState
            +updateTagName(name)
            +togglePhoto(photo)
            +getRecommendedPhotos()
            +saveTag()
        }
        class SelectImageViewModel {
            -DraftTagRepository draftTagRepository
            -RemoteRepository remoteRepository
            +StateFlow~String~ tagName
            +StateFlow~List~Photo~~ selectedPhotos
            +StateFlow~List~Photo~~ allPhotos
            +getAllPhotos()
            +togglePhoto(photo)
        }
        class StoryViewModel {
            -RemoteRepository remoteRepository
            +StateFlow~StoryState~ storyState
            +getStoryByTag(tagId)
            +likeStory(storyId)
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
            +StateFlow~String?~ accessTokenFlow
            +StateFlow~String?~ refreshTokenFlow
            +StateFlow~Boolean~ isLoaded
            +getAccessToken() String?
            +getRefreshToken() String?
            +saveTokens(accessToken, refreshToken)
            +clearTokens()
        }
        class SessionManager {
            -DataStore~Preferences~ dataStore
            -MutableStateFlow~String?~ _accessToken
            -MutableStateFlow~String?~ _refreshToken
            -MutableStateFlow~Boolean~ _isLoaded
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
            +StateFlow~String?~ isLoggedIn
            +StateFlow~Boolean~ isSessionLoaded
            +login(username, password) LoginResult
            +register(email, username, password) RegisterResult
            +refreshTokens() RefreshResult
            +logout()
        }
        class SearchRepository {
            -ApiService apiService
            +semanticSearch(query, offset) SearchResult
        }
        class DraftTagRepository {
            -MutableStateFlow~String~ _tagName
            -MutableStateFlow~List~Photo~~ _selectedPhotos
            +StateFlow~String~ tagName
            +StateFlow~List~Photo~~ selectedPhotos
            +initialize(tagName, photos)
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
    DraftTagRepository ..> Photo : manages
    ImageBrowserRepository --> LocalRepository : uses
    AuthViewModel --> TokenRepository : uses
    HomeViewModel --> RemoteRepository : uses
    HomeViewModel --> TokenRepository : uses
    AlbumViewModel --> RemoteRepository : uses
    LocalViewModel --> LocalRepository : uses
    LocalViewModel ..> Album : manages
    SearchViewModel --> SearchRepository : uses
    ImageDetailViewModel --> ImageBrowserRepository : uses
    ImageDetailViewModel ..> ImageContext : manages
    AddTagViewModel --> DraftTagRepository : uses
    AddTagViewModel --> RemoteRepository : uses
    SelectImageViewModel --> LocalRepository : uses
    SelectImageViewModel --> RecommendRepository : uses
    StoryViewModel --> RemoteRepository : uses
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
    HomeScreen --> HomeViewModel : observes
    HomeScreen --> PhotoViewModel : observes
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
    ```