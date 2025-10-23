import uuid
from django.db import models
from django.contrib.auth.models import User

class Tag(models.Model):
    tag_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag= models.CharField(max_length=50)
    user = models.ForeignKey(User, on_delete=models.CASCADE)

    def __str__(self):
        return self.tag

class Photo_Tag(models.Model):
    pt_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag = models.ForeignKey(Tag, on_delete=models.CASCADE, default=None)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    photo_id = models.UUIDField(max_length=255, unique=True)

    def __str__(self):
        return f"{self.photo_id} tagged with {self.tag.tag}"
    