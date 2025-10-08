from django.urls import path
from . import views

urlpatterns = [
    path('', views.TagNameView.as_view(), name='create-tag'),
    path('<uuid:tag_id>/', views.TagDetailView.as_view(), name='tag-detail'),
]