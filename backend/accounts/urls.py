from django.urls import path
from .views import SignUpView, SignInView, SignOutView, TokenRefreshView

app_name = 'accounts'
urlpatterns = [
    path("signup/", SignUpView.as_view(), name='signup'), 
    path("signin/", SignInView.as_view(), name='signin'), 
    path("signout/", SignOutView.as_view(), name='signout'), 
    path("refresh/", TokenRefreshView.as_view(), name='token_refresh'),
]