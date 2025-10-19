```mermaid
---
config:
  class:
    hideEmptyMembersBox: true
  layout: elk
---
classDiagram
    direction TB
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
        class PhotoUploadData {
            +List~MultipartBody.Part~ photo
            +RequestBody metadata
        }
        class LoginRequest {
            +String username
            +String password
        }
        class LoginResponse {
            +String access_token
            +String refresh_token
        }
        class RegisterRequest {
            +String email
            +String username
            +String password
        }
        class RegisterResponse {
            +Int id
        }
        class RefreshRequest {
            +String refresh_token
        }
        class RefreshResponse {
            +String access_token
        }
        class SemanticSearchResponse {
            +List~Int~ photos
        }
        class LoginState {
        }
        class LoginState_Idle {
        }
        class LoginState_Success {
        }
        class LoginState_BadRequest {
        }
        class LoginState_Unauthorized {
        }
        class LoginState_NetworkError {
        }
        class LoginState_Error {
        }
        class RegisterState {
        }
        class RegisterState_Idle {
        }
        class RegisterState_Success {
        }
        class RegisterState_BadRequest {
        }
        class RegisterState_Conflict {
        }
        class RegisterState_NetworkError {
        }
        class RegisterState_Error {
        }
        class RefreshState {
        }
        class RefreshState_Idle {
        }
        class RefreshState_Success {
        }
        class RefreshState_Unauthorized {
        }
        class RefreshState_Error {
        }
        class RefreshState_NetworkError {
        }
        class LogoutState {
        }
        class LogoutState_Idle {
        }
        class LogoutState_Loading {
        }
        class LogoutState_Success {
        }
        class LogoutState_Error {
        }
        class HomeScreenUiState {
            +Boolean isLoading
            +String? userMessage
            +Boolean isUploadSuccess
        }
        class SemanticSearchState {
        }
        class SemanticSearchState_Idle {
        }
        class SemanticSearchState_Loading {
        }
        class SemanticSearchState_Success {
        }
        class SemanticSearchState_Empty {
        }
        class SemanticSearchState_NetworkError {
        }
        class SemanticSearchState_Error {
        }
        class ImageContext {
            +List~Uri~ images
            +Int currentIndex
            +ContextType contextType
        }
        class ContextType {
            ALBUM
            TAG_ALBUM
            SEARCH_RESULT
            GALLERY
        }
        class SessionStore {
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
        class LoginResult {
        }
        class LoginResult_Success {
        }
        class LoginResult_BadRequest {
            +String message
        }
        class LoginResult_Unauthorized {
            +String message
        }
        class LoginResult_NetworkError {
            +String message
        }
        class LoginResult_Error {
            +String message
        }
        class RegisterResult {
        }
        class RegisterResult_Success {
            +Int userId
        }
        class RegisterResult_BadRequest {
            +String message
        }
        class RegisterResult_Conflict {
            +String message
        }
        class RegisterResult_NetworkError {
            +String message
        }
        class RegisterResult_Error {
            +String message
        }
        class RefreshResult {
        }
        class RefreshResult_Success {
        }
        class RefreshResult_Unauthorized {
        }
        class RefreshResult_NetworkError {
            +String message
        }
        class RefreshResult_Error {
            +String message
        }
        class SearchRepository {
            -ApiService apiService
            +semanticSearch(query, offset) SearchResult
        }
        class SearchResult {
        }
        class SearchResult_Success {
            +List~Int~ photoIds
        }
        class SearchResult_Empty {
            +String query
        }
        class SearchResult_BadRequest {
            +String message
        }
        class SearchResult_Unauthorized {
            +String message
        }
        class SearchResult_NetworkError {
            +String message
        }
        class SearchResult_Error {
            +String message
        }
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
            +StateFlow~ImageContext?~ imageContext
            +setImageContext(context)
            +clearImageContext()
            +setSingleImage(uri)
        }
        class ViewModelFactory {
            -Context context
            -SessionStore sessionStore
            -TokenRepository tokenRepository
            -SearchRepository searchRepository
            +create(modelClass) ViewModel
        }
        class Screen {
            +String route
        }
        class Screen_Home {
            +String route
        }
        class Screen_Album {
            +String route
            +createRoute(tagName) String
        }
        class Screen_Image {
            +String route
            +createRoute(uri) String
        }
        class Screen_LocalGallery {
            +String route
        }
        class Screen_LocalAlbum {
            +String route
            +createRoute(id, name) String
        }
        class Screen_SearchResult {
            +String route
            +createRoute(query) String
        }
        class Screen_Login {
            +String route
        }
        class Screen_Register {
            +String route
        }
        class AppNavigation {
            +NavController navController
            +SessionManager sessionManager
            +ImageDetailViewModel imageDetailViewModel
        }
        class MainActivity {
            +onCreate(savedInstanceState)
        }
        <<sealed>> LoginState
        <<sealed>> RegisterState
        <<sealed>> RefreshState
        <<sealed>> LogoutState
        <<sealed>> SemanticSearchState
        <<enumeration>> ContextType
        <<interface>> SessionStore
        <<interface>> ApiService
        <<sealed>> LoginResult
        <<sealed>> RegisterResult
        <<sealed>> RefreshResult
        <<sealed>> SearchResult
        <<sealed>> Screen
        <<composable>> AppNavigation
        LoginState <|-- LoginState_Idle
        LoginState <|-- LoginState_Success
        LoginState <|-- LoginState_BadRequest
        LoginState <|-- LoginState_Unauthorized
        LoginState <|-- LoginState_NetworkError
        LoginState <|-- LoginState_Error
        RegisterState <|-- RegisterState_Idle
        RegisterState <|-- RegisterState_Success
        RegisterState <|-- RegisterState_BadRequest
        RegisterState <|-- RegisterState_Conflict
        RegisterState <|-- RegisterState_NetworkError
        RegisterState <|-- RegisterState_Error
        RefreshState <|-- RefreshState_Idle
        RefreshState <|-- RefreshState_Success
        RefreshState <|-- RefreshState_Unauthorized
        RefreshState <|-- RefreshState_Error
        RefreshState <|-- RefreshState_NetworkError
        LogoutState <|-- LogoutState_Idle
        LogoutState <|-- LogoutState_Loading
        LogoutState <|-- LogoutState_Success
        LogoutState <|-- LogoutState_Error
        SemanticSearchState <|-- SemanticSearchState_Idle
        SemanticSearchState <|-- SemanticSearchState_Loading
        SemanticSearchState <|-- SemanticSearchState_Success
        SemanticSearchState <|-- SemanticSearchState_Empty
        SemanticSearchState <|-- SemanticSearchState_NetworkError
        SemanticSearchState <|-- SemanticSearchState_Error
        LoginResult <|-- LoginResult_Success
        LoginResult <|-- LoginResult_BadRequest
        LoginResult <|-- LoginResult_Unauthorized
        LoginResult <|-- LoginResult_NetworkError
        LoginResult <|-- LoginResult_Error
        RegisterResult <|-- RegisterResult_Success
        RegisterResult <|-- RegisterResult_BadRequest
        RegisterResult <|-- RegisterResult_Conflict
        RegisterResult <|-- RegisterResult_NetworkError
        RegisterResult <|-- RegisterResult_Error
        RefreshResult <|-- RefreshResult_Success
        RefreshResult <|-- RefreshResult_Unauthorized
        RefreshResult <|-- RefreshResult_NetworkError
        RefreshResult <|-- RefreshResult_Error
        SearchResult <|-- SearchResult_Success
        SearchResult <|-- SearchResult_Empty
        SearchResult <|-- SearchResult_BadRequest
        SearchResult <|-- SearchResult_Unauthorized
        SearchResult <|-- SearchResult_NetworkError
        SearchResult <|-- SearchResult_Error
        Screen <|-- Screen_Home
        Screen <|-- Screen_Album
        Screen <|-- Screen_Image
        Screen <|-- Screen_LocalGallery
        Screen <|-- Screen_LocalAlbum
        Screen <|-- Screen_SearchResult
        Screen <|-- Screen_Login
        Screen <|-- Screen_Register
        SessionStore <|.. SessionManager
        ImageContext o-- ContextType
        LocalRepository ..> PhotoUploadData : creates
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
        TokenRepository ..> LoginResult : returns
        TokenRepository ..> RegisterResult : returns
        TokenRepository ..> RefreshResult : returns
        SearchRepository ..> SearchResult : returns
        ApiService ..> LoginRequest : accepts
        ApiService ..> LoginResponse : returns
        ApiService ..> RegisterRequest : accepts
        ApiService ..> RegisterResponse : returns
        ApiService ..> RefreshRequest : accepts
        ApiService ..> RefreshResponse : returns
        ApiService ..> SemanticSearchResponse : returns
        ApiService ..> Tag : returns
        ApiService ..> Photo : returns
        ApiService ..> PhotoUploadData : accepts
        AuthViewModel --> TokenRepository : uses
        AuthViewModel ..> LoginState : manages
        AuthViewModel ..> RegisterState : manages
        AuthViewModel ..> RefreshState : manages
        AuthViewModel ..> LogoutState : manages
        PhotoViewModel --> RemoteRepository : uses
        PhotoViewModel --> LocalRepository : uses
        PhotoViewModel ..> HomeScreenUiState : manages
        LocalViewModel --> LocalRepository : uses
        LocalViewModel ..> Album : manages
        ServerViewModel --> RemoteRepository : uses
        ServerViewModel ..> Tag : manages
        ServerViewModel ..> Photo : manages
        SearchViewModel --> SearchRepository : uses
        SearchViewModel ..> SemanticSearchState : manages
        ImageDetailViewModel ..> ImageContext : manages
        ViewModelFactory ..> AuthViewModel : creates
        ViewModelFactory ..> PhotoViewModel : creates
        ViewModelFactory ..> LocalViewModel : creates
        ViewModelFactory ..> ServerViewModel : creates
        ViewModelFactory ..> SearchViewModel : creates
        ViewModelFactory ..> ImageDetailViewModel : creates
        ViewModelFactory --> SessionStore : uses
        ViewModelFactory --> TokenRepository : uses
        ViewModelFactory --> SearchRepository : uses
        ViewModelFactory ..> RemoteRepository : creates
        ViewModelFactory ..> LocalRepository : creates
        AppNavigation ..> Screen : uses
        AppNavigation --> SessionManager : uses
        AppNavigation --> ImageDetailViewModel : shares
        AppNavigation ..> ViewModelFactory : uses
        MainActivity ..> AppNavigation : hosts
```