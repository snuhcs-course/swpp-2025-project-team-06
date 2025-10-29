import uuid
from django.db import models
from django.contrib.auth.models import User


class Tag(models.Model):
    tag_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag = models.CharField(max_length=50)
    user = models.ForeignKey(User, on_delete=models.CASCADE)

    def __str__(self):
        return self.tag


class Photo_Tag(models.Model):
    pt_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    tag = models.ForeignKey(Tag, on_delete=models.CASCADE, default=None)
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    photo_id = models.UUIDField(max_length=255)

    def __str__(self):
        return f"{self.photo_id} tagged with {self.tag.tag_id}"


class Caption(models.Model):
    caption_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)

    caption = models.CharField(max_length=50, unique=True)

    def __str__(self):
        return self.caption


class Photo_Caption(models.Model):
    pc_id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE)

    photo_id = models.UUIDField(max_length=255)
    caption = models.ForeignKey(Caption, on_delete=models.CASCADE)

    weight = models.IntegerField(default=0)

    def __str__(self):
        return f"{self.photo_id} captioned with {self.caption}"
