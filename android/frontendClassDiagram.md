```mermaid
classDiagram
    direction TB
    class MainActivity {
            +onCreate(savedInstanceState)
        }
        class Navigation {
            <<Composable>>
            +NavHostController navController
            +authViewModel AuthViewModel
            +photoViewModel PhotoViewModel
            +localViewModel LocalViewModel
            +serverViewModel ServerViewModel
            +searchViewModel SearchViewModel
            +imageDetailViewModel ImageDetailViewModel
            +setupNavGraph()
        }
    namespace View {
        class HomeScreen {
            <<Composable>>
            +navController NavController
            +authViewModel AuthViewModel
            +photoViewModel PhotoViewModel
            +localViewModel LocalViewModel
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
            +serverViewModel ServerViewModel
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
        class ImageScreen {
            <<Composable>>
            +photoPathId Long
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
        class PhotoViewModel {
            -RemoteRepository remoteRepository
            -LocalRepository localRepository
            +StateFlow~HomeScreenUiState~ uiState
            +uploadPhotos()
            +userMessageShown()
        }
        class LocalViewModel {
            -LocalRepository localRepository
            +StateFlow~List~Uri~~ image
            +StateFlow~List~Album~~ albums
            +StateFlow~List~Uri~~ imagesInAlbum
            +getImages()
            +getAlbums()
            +getImagesForAlbum(albumId)
        }
        class ServerViewModel {
            -RemoteRepository remoteRepository
            +StateFlow~List~Tag~~ allTags
            +StateFlow~List~Photo~~ photoByTag
            +getAllTags()
            +getPhotoByTag(tagName)
        }
        class SearchViewModel {
            -SearchRepository searchRepository
            -Context context
            +StateFlow~SemanticSearchState~ searchState
            +search(query, offset)
            +resetSearchState()
        }
        class ImageDetailViewModel {
            -LocalRepository localRepository
            +StateFlow~Uri?~ imageUri
            +StateFlow~ImageContext?~ imageContext
            +loadImage(photoPathId)
            +setImageContext(context)
            +clearImageContext()
            +setSingleImage(uri)
        }
    }
    namespace Model {
        class Tag {
            +String tagName
            +Long thumbnailId
        }
        class Photo {
            +Long photoId
            +List~String~ tags
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
    AuthViewModel --> TokenRepository : uses
    PhotoViewModel --> RemoteRepository : uses
    PhotoViewModel --> LocalRepository : uses
    LocalViewModel --> LocalRepository : uses
    LocalViewModel ..> Album : manages
    ServerViewModel --> RemoteRepository : uses
    ServerViewModel ..> Tag : manages
    ServerViewModel ..> Photo : manages
    SearchViewModel --> SearchRepository : uses
    ImageDetailViewModel --> LocalRepository : uses
    ImageDetailViewModel ..> ImageContext : manages
    MainActivity ..> Navigation : creates
    Navigation --> HomeScreen : routes to
    Navigation --> LoginScreen : routes to
    Navigation --> RegisterScreen : routes to
    Navigation --> AlbumScreen : routes to
    Navigation --> LocalGalleryScreen : routes to
    Navigation --> LocalAlbumScreen : routes to
    Navigation --> ImageScreen : routes to
    Navigation --> SearchResultScreen : routes to
    HomeScreen --> AuthViewModel : observes
    HomeScreen --> PhotoViewModel : observes
    HomeScreen --> LocalViewModel : observes
    LoginScreen --> AuthViewModel : observes
    RegisterScreen --> AuthViewModel : observes
    AlbumScreen --> ServerViewModel : observes
    LocalGalleryScreen --> LocalViewModel : observes
    LocalAlbumScreen --> LocalViewModel : observes
    ImageScreen --> ImageDetailViewModel : observes
    SearchResultScreen --> SearchViewModel : observes
    ```