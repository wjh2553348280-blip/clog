from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_user, get_optional_user
from ..models import Community, Favorite, Follow, Post, PostLike, PostView, User
from ..schemas import ProfileUpdate
from ..serializers import community_out, post_out, user_out

router = APIRouter(tags=["用户与社区"])


@router.get("/communities")
def list_communities(db: Session = Depends(get_db)):
    return [community_out(c) for c in db.query(Community).order_by(Community.id).all()]


@router.get("/users/me/history")
def my_history(db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    """浏览历史：最近看过的帖子（每帖一条，按最后浏览时间倒序）"""
    views = (
        db.query(PostView)
        .filter(PostView.user_id == me.id)
        .order_by(PostView.viewed_at.desc())
        .limit(100)
        .all()
    )
    items = []
    for v in views:
        post = db.get(Post, v.post_id)
        if post is None or post.deleted:
            continue
        liked = db.query(PostLike).filter_by(user_id=me.id, post_id=post.id).first() is not None
        collected = db.query(Favorite).filter_by(user_id=me.id, post_id=post.id).first() is not None
        items.append(
            {
                "post": post_out(post, liked=liked, collected=collected),
                "viewed_at": v.viewed_at,
            }
        )
    return items


@router.get("/users/{user_id}")
def get_user(user_id: int, db: Session = Depends(get_db), me: Optional[User] = Depends(get_optional_user)):
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(404, "用户不存在")
    post_count = db.query(Post).filter_by(author_id=user_id, deleted=False).count()
    follower_count = db.query(Follow).filter_by(following_id=user_id).count()
    following_count = db.query(Follow).filter_by(follower_id=user_id).count()
    followed = False
    if me is not None:
        followed = (
            db.query(Follow).filter_by(follower_id=me.id, following_id=user_id).first() is not None
        )
    return {
        **user_out(user),
        "post_count": post_count,
        "follower_count": follower_count,
        "following_count": following_count,
        "followed": followed,
    }


@router.put("/users/me")
def update_profile(data: ProfileUpdate, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    if data.nickname is not None:
        me.nickname = data.nickname
    if data.avatar_emoji is not None:
        me.avatar_emoji = data.avatar_emoji
    if data.bio is not None:
        me.bio = data.bio
    db.commit()
    db.refresh(me)
    return user_out(me)


@router.get("/users/{user_id}/posts")
def user_posts(user_id: int, db: Session = Depends(get_db)):
    posts = (
        db.query(Post)
        .filter_by(author_id=user_id, deleted=False)
        .order_by(Post.created_at.desc())
        .limit(50)
        .all()
    )
    return [post_out(p) for p in posts]


@router.get("/users/{user_id}/favorites")
def user_favorites(user_id: int, db: Session = Depends(get_db), me: Optional[User] = Depends(get_optional_user)):
    favs = (
        db.query(Favorite, Post)
        .join(Post, Favorite.post_id == Post.id)
        .filter(Favorite.user_id == user_id, Post.deleted == False)
        .order_by(Favorite.created_at.desc())
        .limit(50)
        .all()
    )
    return [post_out(p) for _, p in favs]


@router.get("/users/{user_id}/followers")
def user_followers(user_id: int, db: Session = Depends(get_db)):
    rows = db.query(Follow, User).join(User, Follow.follower_id == User.id).filter(Follow.following_id == user_id).all()
    return [user_out(u) for _, u in rows]


@router.get("/users/{user_id}/following")
def user_following(user_id: int, db: Session = Depends(get_db)):
    rows = db.query(Follow, User).join(User, Follow.following_id == User.id).filter(Follow.follower_id == user_id).all()
    return [user_out(u) for _, u in rows]
