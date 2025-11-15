from django.urls import path
from . import views

app_name = 'search'

urlpatterns = [
    path('semantic/', views.SemanticSearchView.as_view(), name='semantic-search'),
]