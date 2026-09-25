from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_user, get_optional_user
from ..models import Follow, Post, PostLike, Favorite
from ..serializers import post_out

router = APIRouter(tags=["信息流"])

# 热度公式参考 Reddit/Hacker News（PDF 建议）：
# 热度 = (点赞 + 2×评论 + 3×收藏 − 5×举报) / (发布小时数 + 2)^1.5
# 冷启动保底：新帖没有互动数据也有一段时间的保底权重（时间衰减分母自带）


def _hot_score(post: Post, now: datetime) -> float:
    hours = max(0.0, (now - post.created_at).total_seconds() / 3600)
    numerator = (
        post.like_count
        + 2.0 * post.comment_count
        + 3.0 * post.collect_count
        - 5.0 * post.report_count
    )
    return numerator / ((hours + 2.0) ** 1.5)


def _ranked_posts(db: Session, query, now: datetime, limit: int = 50):
    """取最近 7 天的帖子，按热度排序（MVP 规模够用；量大后换 SQL 排序）"""
    posts = query.filter(Post.deleted == False, Post.created_at >= now - _7days(now)).limit(300).all()
    posts.sort(key=lambda p: _hot_score(p, now), reverse=True)
    return posts[:limit]


def _7days(now: datetime):
    from datetime import timedelta

    return timedelta(days=7)


@router.get("/feed")
def hot_feed(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    me: Optional[object] = Depends(get_optional_user),
):
    now = datetime.utcnow()
    posts = _ranked_posts(db, db.query(Post), now, limit=page * size)
    posts = posts[(page - 1) * size : page * size]
    liked_ids, collected_ids = _my_flags(db, me, posts)
    return [
        post_out(p, liked=p.id in liked_ids, collected=p.id in collected_ids) for p in posts
    ]


@router.get("/hot")
def hot_ranking(
    db: Session = Depends(get_db),
    me: Optional[object] = Depends(get_optional_user),
):
    """热榜：最近 24 小时帖子按热度排序（贴吧热议榜思路）。

    热度公式（公开透明，不搞黑箱）：
        热度 = 阅读×0.2 + 点赞×2 + 评论×5 + 收藏×3
    24 小时窗口内无帖时放宽到 72 小时（冷启动社区保底）。
    """
    now = datetime.utcnow()
    posts = (
        db.query(Post)
        .filter(Post.deleted == False, Post.created_at >= now - timedelta(hours=24))
        .limit(300)
        .all()
    )
    if not posts:
        posts = (
            db.query(Post)
            .filter(Post.deleted == False, Post.created_at >= now - timedelta(hours=72))
            .limit(300)
            .all()
        )

    def heat(p: Post) -> float:
        return p.view_count * 0.2 + p.like_count * 2 + p.comment_count * 5 + p.collect_count * 3

    posts.sort(key=heat, reverse=True)
    posts = posts[:20]
    liked_ids, collected_ids = _my_flags(db, me, posts)
    return [
        {
            "post": post_out(p, liked=p.id in liked_ids, collected=p.id in collected_ids),
            "hot_score": round(heat(p), 1),
        }
        for p in posts
    ]


@router.get("/feed/community/{community_id}")
def community_feed(
    community_id: int,
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    me: Optional[object] = Depends(get_optional_user),
):
    now = datetime.utcnow()
    posts = _ranked_posts(db, db.query(Post).filter_by(community_id=community_id), now, limit=page * size)
    posts = posts[(page - 1) * size : page * size]
    liked_ids, collected_ids = _my_flags(db, me, posts)
    return [
        post_out(p, liked=p.id in liked_ids, collected=p.id in collected_ids) for p in posts
    ]


@router.get("/feed/following")
def following_feed(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    me: object = Depends(get_current_user),
):
    followed_ids = [f.following_id for f in db.query(Follow).filter_by(follower_id=me.id).all()]
    if not followed_ids:
        return []
    posts = (
        db.query(Post)
        .filter(Post.deleted == False, Post.author_id.in_(followed_ids))
        .order_by(Post.created_at.desc())
        .offset((page - 1) * size)
        .limit(size)
        .all()
    )
    liked_ids, collected_ids = _my_flags(db, me, posts)
    return [
        post_out(p, liked=p.id in liked_ids, collected=p.id in collected_ids) for p in posts
    ]


def _my_flags(db: Session, me, posts):
    liked_ids: set = set()
    collected_ids: set = set()
    if me is None or not posts:
        return liked_ids, collected_ids
    ids = [p.id for p in posts]
    liked_ids = {x.post_id for x in db.query(PostLike).filter_by(user_id=me.id).filter(PostLike.post_id.in_(ids))}
    collected_ids = {x.post_id for x in db.query(Favorite).filter_by(user_id=me.id).filter(Favorite.post_id.in_(ids))}
    return liked_ids, collected_ids
