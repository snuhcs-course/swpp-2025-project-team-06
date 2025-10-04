```mermaid
classDiagram

    subgraph
        class MainActivity
        class AppNavigation
        class Screen
        class HomeScreen
        class AlbumScreen
        class ImageScreen
        class LocalGalleryScreen
        class LocalAlbumScreen
        class AddTagScreen
        class StoryScreen
    end
    
    subgraph
        class LocalViewModel {
            +image: StateFlow<List<Uri>>
            +albums: StateFlow<List<Album>>
            +imagesInAlbum: StateFlow<List<Uri>>
            +getImages()
            +getAlbums()
            +getImagesForAlbum(albumId: Long)
        }
        class ServerViewModel {
            +allTags: StateFlow<List<Tag>>
            +photoByTag: StateFlow<List<Photo>>
            +getAllTags()
            +getPhotoByTag(tagName: String)
            +addTagToPhoto(photoId: Long, tagName: String)
            +getAmbiguousPhotos() : List<Photo>
        }
        class ViewModelFactory {
            +create(modelClass: Class<T>): T
        }
    end

    subgraph
        class LocalRepository {
            -context: Context
            +getImages(): List<Uri>
            +getAlbums(): List<Album>
            +getImagesForAlbum(albumId: Long): List<Uri>
        }
        class RemoteRepository {
            -apiService: ApiService
            +getAllTags(): List<Tag>
            +getPhotosByTag(tagName: String): List<Photo>
            +addTagToPhoto(photoId: Long, tagName: String)
            +getAmbiguousPhotos() : List<Photo>
        }
    end

    subgraph
        class ApiService {
            <<Interface>>
            +getHomeTags(): List<Tag>
            +getPhotosByTag(tagName: String): List<Photo>
            +addTagToPhoto(photoId: Long, tagName: String)
            +getAmbiguousPhotos() : List<Photo>
        }
        class RetrofitInstance {
            +api: ApiService
        }
        class Context {
            <<Android Framework>>
        }
    end

    subgraph
        class Tag {
            <<Data>> 
            tagName: String
            thumbnailId: Long
        }
        class Photo {
            <<Data>>
            photoId: Long
            tags: List<String>
        }
        class Album {
            <<Data>>
            albumId: Long
            albumName: String
            thumbnailUri
        }
    end

    HomeScreen --|> LocalViewModel : observes
    HomeScreen --|> ServerViewModel : observes
    AlbumScreen --|> LocalViewModel : observes
    ImageScreen --|> LocalViewModel : observes
    LocalGalleryScreen --|> LocalViewModel : observes
    LocalAlbumScreen --|> LocalViewModel : observes
    AddTagScreen --|> LocalViewModel : observes
    StoryScreen --|> ServerViewModel : observes

    MainActivity --> AppNavigation : contains
    AppNavigation --> Screen : uses routes
    AppNavigation --> HomeScreen : navigates to
    AppNavigation --> AlbumScreen : navigates to
    AppNavigation --> ImageScreen : navigates to
    AppNavigation --> LocalGalleryScreen : navigates to
    AppNavigation --> LocalAlbumScreen : navigates to
    AppNavigation --> AddTagScreen : navigates to
    AppNavigation --> StoryScreen : navigates to

    HomeScreen --> AddTagScreen : triggers navigation
    HomeScreen --> ImageScreen : triggers navigation
    HomeScreen --> AlbumScreen : triggers navigation
    HomeScreen --> StoryScreen : triggers navigation
    AlbumScreen --> ImageScreen : triggers navigation
    LocalGalleryScreen --> LocalAlbumScreen : triggers navigation
    LocalGalleryScreen --> HomeScreen : triggers navigation
    StoryScreen --> HomeScreen : triggers navigation

    HomeScreen --|> ServerViewModel : calls getAllTags
    AlbumScreen --|> LocalViewModel : calls getImages
    AlbumScreen --|> ServerViewModel : calls addTagToPhot
    ImageScreen --|> ServerViewModel : calls addTagToPhoto
    LocalGalleryScreen --|> LocalViewModel : calls getAlbums
    LocalAlbumScreen --|> LocalViewModel : getImagesForAlbum
    AddTagScreen --|> LocalViewModel : calls getImages
    AddTagScreen --|> ServerViewModel : calls addTagToPhoto
    StoryScreen --|> ServerViewModel : calls getAmbiguousPhotos


    ViewModelFactory ..> LocalViewModel : creates
    ViewModelFactory ..> ServerViewModel : creates
    ViewModelFactory --> LocalRepository : depends on
    ViewModelFactory --> RemoteRepository : depends on

    LocalViewModel --> LocalRepository : uses
    ServerViewModel --> RemoteRepository : uses
    LocalRepository --> Context : uses
    RemoteRepository --> ApiService : uses
    RetrofitInstance ..> ApiService : provides

    ServerViewModel o-- Tag
    ServerViewModel o-- Photo
    LocalViewModel o-- Album
