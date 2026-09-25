from __future__ import annotations

import json
from typing import Optional

from .models import Comment, Community, Post, User


def user_out(u: User) -> dict:
    return {
        "id": u.id,
        "username": u.username,
        "nickname": u.nickname,
        "avatar_emoji": u.avatar_emoji,
        "bio": u.bio,
        "created_at": u.created_at,
    }


def community_out(c: Community) -> dict:
    return {
        "id": c.id,
        "name": c.name,
        "slug": c.slug,
        "description": c.description,
        "icon": c.icon,
        "post_count": c.post_count,
    }


def _media_list(media_json: Optional[str]):
    try:
        data = json.loads(media_json or "[]")
        return data if isinstance(data, list) else []
    except Exception:
        return []


def post_out(p: Post, liked: bool = False, collected: bool = False) -> dict:
    return {
        "id": p.id,
        "title": p.title,
        "content": p.content,
        "media": _media_list(p.media),
        "created_at": p.created_at,
        "like_count": p.like_count,
        "comment_count": p.comment_count,
        "collect_count": p.collect_count,
        "view_count": p.view_count,
        "liked": liked,
        "collected": collected,
        "author": user_out(p.author),
        "community": community_out(p.community),
    }


def comment_out(c: Comment, liked: bool = False) -> dict:
    return {
        "id": c.id,
        "content": c.content,
        "like_count": c.like_count,
        "created_at": c.created_at,
        "liked": liked,
        "user": user_out(c.user),
        "parent_id": c.parent_id,
        "reply_count": c.reply_count,
    }
