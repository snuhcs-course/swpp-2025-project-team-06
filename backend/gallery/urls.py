from django.urls import path
from . import views

urlpatterns = [
    path('', views.PhotosView.as_view(), name='image-upload'),
    path('batch/', views.PhotoBatchView.as_view(), name='batch-image-upload'),
    path('<uuid:photo_id>/', views.PhotoDetailView.as_view(), name='delete-photo'),
    path('<uuid:tag_id>/', views.TaggedPhotoListView.as_view(), name='get-photos-by-tag'),
    path('detail/<uuid:photo_id>/', views.PhotoDetailView.as_view(), name='get-photo-detail'),
]