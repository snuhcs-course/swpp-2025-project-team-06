from django.urls import path
from . import views

urlpatterns = [
    path('', views.PostPhotoView.as_view(), name='post-image-upload'),
    path('batch/', views.PostDeletePhotoBatchView.as_view(), name='post-image-upload-batch'),
    path('<uuid:photo_id>/', views.DeletePhotoDetailView.as_view(), name='delete-photo'),
    path('<uuid:tag_id>/', views.GetTaggedPhotoListView.as_view(), name='get-photos-by-tag'),
    path('detail/<uuid:photo_id>/', views.PostPhotoDetailView.as_view(), name='get-photo-detail'),
    path('<uuid:photo_id>/tags/', views.PostPhotoTagsView.as_view(), name='post-photo-tags'),
    path('<uuid:photo_id>/tags/<uuid:tag_id>/', views.DeletePhotoTagsView.as_view(), name='delete-photo-tags'),
]