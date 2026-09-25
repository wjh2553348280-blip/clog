from __future__ import annotations

import json
import uuid
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, UploadFile
from sqlalchemy.orm import Session

from ..config import MAX_IMAGE_SIZE, MAX_VIDEO_SIZE, UPLOAD_DIR, STATIC_URL_PREFIX
from ..database import get_db
from ..deps import get_current_user, get_optional_user
from ..models import Community, Post, PostLike, PostView, Favorite, User, now
from ..schemas import MediaItem, PostCreate, ReportCreate
from ..serializers import post_out

router = APIRouter(tags=["帖子与媒体"])


def _check_media(media: list[MediaItem]) -> None:
    if len(media) > 9:
        raise HTTPException(400, "最多 9 个媒体文件")
    for item in media:
        if item.type not in ("image", "video"):
            raise HTTPException(400, "媒体类型只能是 image 或 video")
        if not item.url.startswith(STATIC_URL_PREFIX):
            raise HTTPException(400, "媒体地址不合法")


@router.post("/posts")
def create_post(data: PostCreate, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    community = db.get(Community, data.community_id)
    if community is None:
        raise HTTPException(400, "社区不存在")
    _check_media(data.media)

    post = Post(
        author_id=me.id,
        community_id=community.id,
        title=data.title,
        content=data.content,
        media=json.dumps([m.model_dump() for m in data.media], ensure_ascii=False),
    )
    community.post_count += 1
    db.add(post)
    db.commit()
    db.refresh(post)
    return post_out(post)


@router.get("/posts/{post_id}")
def get_post(post_id: int, db: Session = Depends(get_db), me: Optional[User] = Depends(get_optional_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")
    # 记浏览历史；view_count 只对「首次浏览」+1，约等于看过的人数
    if me is not None:
        view = db.query(PostView).filter_by(user_id=me.id, post_id=post_id).first()
        if view is None:
            db.add(PostView(user_id=me.id, post_id=post_id))
            post.view_count += 1
        else:
            view.viewed_at = now()
    else:
        post.view_count += 1
    liked = collected = False
    if me is not None:
        liked = db.query(PostLike).filter_by(user_id=me.id, post_id=post_id).first() is not None
        collected = db.query(Favorite).filter_by(user_id=me.id, post_id=post_id).first() is not None
    db.commit()
    return post_out(post, liked=liked, collected=collected)


@router.delete("/posts/{post_id}")
def delete_post(post_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")
    if post.author_id != me.id:
        raise HTTPException(403, "只能删除自己的帖子")
    post.deleted = True
    community = db.get(Community, post.community_id)
    if community and community.post_count > 0:
        community.post_count -= 1
    db.commit()
    return {"ok": True}


@router.post("/posts/{post_id}/report")
def report_post(post_id: int, data: ReportCreate, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")
    from ..models import Report

    post.report_count += 1
    db.add(Report(reporter_id=me.id, post_id=post_id, reason=data.reason))
    db.commit()
    return {"ok": True}


@router.post("/upload/media")
async def upload_media(file: UploadFile, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    """上传图片或视频，返回可访问的 URL"""
    content = await file.read()

    if file.content_type and file.content_type.startswith("video/"):
        if len(content) > MAX_VIDEO_SIZE:
            raise HTTPException(400, "视频不能超过 60MB")
        kind = "video"
    elif file.content_type and file.content_type.startswith("image/"):
        if len(content) > MAX_IMAGE_SIZE:
            raise HTTPException(400, "图片不能超过 10MB")
        kind = "image"
    else:
        raise HTTPException(400, "只支持图片或视频文件")

    ext = (Path(file.filename or "").suffix or ".bin").lower()
    if ext not in (".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".mov", ".webm"):
        ext = ".mp4" if kind == "video" else ".jpg"

    name = f"{uuid.uuid4().hex}{ext}"
    (UPLOAD_DIR / name).write_bytes(content)
    return {"type": kind, "url": f"{STATIC_URL_PREFIX}/{name}"}
