from django.urls import path
from . import views

urlpatterns = [
    path('semantic/', views.SemanticSearchView.as_view(), name='semantic-search'),
]