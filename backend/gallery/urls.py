from django.urls import path
from . import views

urlpatterns = [
    path('photos/', views.PhotoView.as_view()), # get, post
    path('photos/<uuid:photo_id>/', views.PhotoDetailView.as_view()), # get, delete
    path('photos/bulk-delete/', views.BulkDeletePhotoView.as_view()), # post
    path('photos/albums/<uuid:tag_id>/', views.GetPhotosByTagView.as_view()), # get
    path('photos/<uuid:photo_id>/tags/', views.PostPhotoTagsView.as_view()), # post
    path('photos/<uuid:photo_id>/tags/<uuid:tag_id>/', views.DeletePhotoTagsView.as_view()), # delete

    path('tags/', views.TagView.as_view()), # get, post
    path('tags/<uuid:tag_id>/', views.TagDetailView.as_view()), #get, delete, put
]