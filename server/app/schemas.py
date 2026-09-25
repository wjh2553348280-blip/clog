from typing import List, Optional

from pydantic import BaseModel, Field


# ---------- 认证 ----------
class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=6, max_length=64)
    nickname: str = Field(min_length=1, max_length=32)
    avatar_emoji: str = "🐱"


class UserLogin(BaseModel):
    username: str
    password: str


class ProfileUpdate(BaseModel):
    nickname: Optional[str] = Field(default=None, min_length=1, max_length=32)
    avatar_emoji: Optional[str] = None
    bio: Optional[str] = Field(default=None, max_length=140)


# ---------- 帖子 ----------
class MediaItem(BaseModel):
    type: str  # image / video
    url: str


class PostCreate(BaseModel):
    title: str = Field(min_length=1, max_length=20)
    content: str = Field(min_length=1, max_length=2000)
    community_id: int
    media: List[MediaItem] = []


class CommentCreate(BaseModel):
    content: str = Field(min_length=1, max_length=1000)
    parent_id: Optional[int] = None  # 有值 = 回复某楼层（楼中楼）


class ReportCreate(BaseModel):
    reason: str = Field(default="", max_length=200)
