from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_optional_user
from ..models import Community, Post, PostLike, Favorite, User
from ..serializers import community_out, post_out, user_out

router = APIRouter(tags=["搜索"])


@router.get("/search")
def search(
    q: str = Query(..., min_length=1, max_length=50),
    db: Session = Depends(get_db),
    me: object = Depends(get_optional_user),
):
    like_q = f"%{q}%"

    posts = (
        db.query(Post)
        .filter(Post.deleted == False, or_(Post.title.like(like_q), Post.content.like(like_q)))
        .order_by(Post.created_at.desc())
        .limit(10)
        .all()
    )
    users = (
        db.query(User)
        .filter(User.username.like(like_q) | User.nickname.like(like_q))
        .limit(10)
        .all()
    )
    communities = db.query(Community).filter(Community.name.like(like_q)).limit(5).all()

    liked_ids: set = set()
    if me is not None and posts:
        ids = [p.id for p in posts]
        liked_ids = {x.post_id for x in db.query(PostLike).filter_by(user_id=me.id).filter(PostLike.post_id.in_(ids))}

    return {
        "posts": [post_out(p, liked=p.id in liked_ids) for p in posts],
        "users": [user_out(u) for u in users],
        "communities": [community_out(c) for c in communities],
    }
