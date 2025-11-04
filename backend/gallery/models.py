import uuid
from django.db import models
from django.contrib.auth.models import User


class Tag(models.Model):
    tag_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag = models.CharField(max_length=50)
    user = models.ForeignKey(User, on_delete=models.CASCADE)

    def __str__(self):
        return self.tag
    

class Photo(models.Model):
    photo_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    photo_path_id = models.IntegerField()
    filename = models.CharField(max_length=255)
    created_at = models.DateTimeField()
    lat = models.FloatField(null=True, blank=True)
    lng = models.FloatField(null=True, blank=True)
    is_tagged = models.BooleanField(default=False)

    def __str__(self):
        return f"Photo {self.photo_id} by User {self.user.id}"


class Photo_Tag(models.Model):
    pt_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag = models.ForeignKey(Tag, on_delete=models.CASCADE)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    photo = models.ForeignKey(Photo, on_delete=models.CASCADE, default=None)

    def __str__(self):
        return f"{self.photo.photo_id} tagged with {self.tag.tag_id}"


class Caption(models.Model):
    caption_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    caption = models.CharField(max_length=50)

    class Meta:
        unique_together = ('user', 'caption')
    def __str__(self):
        return self.caption


class Photo_Caption(models.Model):
    pc_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    photo = models.ForeignKey(Photo, on_delete=models.CASCADE, default=None)
    caption = models.ForeignKey(Caption, on_delete=models.CASCADE)
    weight = models.IntegerField(default=0)

    def __str__(self):
        return f"{self.photo.photo_id} captioned with {self.caption.caption}"
