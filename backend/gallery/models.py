import uuid
from django.db import models
from django.contrib.auth.models import User

class Tag(models.Model):
    tag_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    
    tag= models.CharField(max_length=50, unique=True)
    embedding = models.JSONField()

    def __str__(self):
        return self.tag

class Photo_Tag(models.Model):
    pt_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    
    photo_id = models.UUIDField(max_length=255, unique=True)
    tag_id = models.UUIDField(max_length=255)

    def __str__(self):
        return f"{self.photo_id} tagged with {self.tag_id}"