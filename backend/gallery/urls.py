from django.urls import path
from . import views

app_name = "gallery"
urlpatterns = [
    path("photos/", views.PhotoView.as_view(), name="photos"),  # get, post
    path(
        "photos/<uuid:photo_id>/", views.PhotoDetailView.as_view(), name="photo_detail"
    ),  # get, delete
    path(
        "photos/bulk-delete/",
        views.BulkDeletePhotoView.as_view(),
        name="photos_bulk_delete",
    ),  # post
    path(
        "photos/albums/<uuid:tag_id>/",
        views.GetPhotosByTagView.as_view(),
        name="photos_by_tag",
    ),  # get
    path(
        "photos/<uuid:photo_id>/tags/",
        views.PostPhotoTagsView.as_view(),
        name="photo_tags",
    ),  # post
    path(
        "photos/<uuid:photo_id>/tags/<uuid:tag_id>/",
        views.DeletePhotoTagsView.as_view(),
        name="delete_photo_tag",
    ),  # delete
    path(
        "photos/<uuid:photo_id>/recommendation", views.GetRecommendTagView.as_view()
    ),  # get
    path("tags/", views.TagView.as_view(), name="tags"),  # get, post
    path(
        "tags/<uuid:tag_id>/",
        views.TagDetailView.as_view(),
        name="tag_detail",
    ),  # get, delete, put
    path(
        "tags/<uuid:tag_id>/recommendation/",
        views.PhotoRecommendationView.as_view(),
        name="photo_recommendation",
    ),  # get
    path(
        "stories/",
        views.StoryView.as_view(),
        name="stories",
        # get
    )
]
