from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_user, get_optional_user
from ..models import (
    Comment,
    CommentLike,
    Favorite,
    Follow,
    Notification,
    Post,
    PostLike,
    User,
)
from ..schemas import CommentCreate
from ..serializers import comment_out

router = APIRouter(tags=["互动"])


def _notify(db: Session, user_id: int, type_: str, actor_id: int, post_id=None, comment_id=None):
    """给自己以外的用户发通知；自己给自己点赞不通知"""
    if user_id == actor_id:
        return
    db.add(Notification(user_id=user_id, type=type_, actor_id=actor_id, post_id=post_id, comment_id=comment_id))


# ---------- 点赞 ----------
@router.post("/posts/{post_id}/like")
def toggle_like(post_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")
    like = db.query(PostLike).filter_by(user_id=me.id, post_id=post_id).first()
    if like is None:
        db.add(PostLike(user_id=me.id, post_id=post_id))
        post.like_count += 1
        _notify(db, post.author_id, "like", me.id, post_id=post_id)
        liked = True
    else:
        db.delete(like)
        post.like_count = max(0, post.like_count - 1)
        liked = False
    db.commit()
    return {"liked": liked, "like_count": post.like_count}


# ---------- 收藏 ----------
@router.post("/posts/{post_id}/favorite")
def toggle_favorite(post_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")
    fav = db.query(Favorite).filter_by(user_id=me.id, post_id=post_id).first()
    if fav is None:
        db.add(Favorite(user_id=me.id, post_id=post_id))
        post.collect_count += 1
        collected = True
    else:
        db.delete(fav)
        post.collect_count = max(0, post.collect_count - 1)
        collected = False
    db.commit()
    return {"collected": collected, "collect_count": post.collect_count}


# ---------- 评论（贴吧式：楼层 + 楼中楼） ----------
REPLY_PREVIEW = 3  # 每层楼默认展示的楼中楼条数，超出部分走「展开全部」


@router.get("/posts/{post_id}/comments")
def list_comments(
    post_id: int,
    sort: str = Query("new", pattern="^(hot|new)$"),
    author_only: bool = Query(False),
    db: Session = Depends(get_db),
    me: Optional[User] = Depends(get_optional_user),
):
    """楼层 + 楼中楼：顶层评论为楼层，回复挂在楼层下。

    - sort=new（默认）：楼层按时间正序（贴吧式 1楼 2楼…，热评置顶会打乱楼中楼阅读顺序，故默认正序）
    - sort=hot：热评在前（赞多优先）；author_only=true：只看楼主
    - 楼号是身份标识：只看楼主等过滤不改变楼号，被过滤的楼层会跳号（贴吧行为）
    """
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")

    # 先给全部楼层编楼号（按时间正序），再套过滤/排序——楼号始终稳定
    all_floors = (
        db.query(Comment)
        .filter_by(post_id=post_id, deleted=False, parent_id=None)
        .order_by(Comment.created_at.asc(), Comment.id.asc())
        .all()
    )
    number_map = {f.id: i + 1 for i, f in enumerate(all_floors)}

    floors = list(all_floors)
    if author_only:
        floors = [f for f in floors if f.user_id == post.author_id]
    if sort == "hot":
        floors.sort(key=lambda c: (-c.like_count, c.created_at))

    # 一次取回所有楼中楼，按楼层分组
    replies_q = db.query(Comment).filter(
        Comment.parent_id.in_([f.id for f in floors] or [-1]),
        Comment.deleted == False,
    )
    if author_only:
        replies_q = replies_q.filter(Comment.user_id == post.author_id)
    replies = replies_q.order_by(Comment.created_at.asc()).all()
    by_parent = {}
    for r in replies:
        by_parent.setdefault(r.parent_id, []).append(r)

    all_ids = [f.id for f in floors] + [r.id for r in replies]
    my_likes = set()
    if me is not None and all_ids:
        my_likes = {
            x.comment_id
            for x in db.query(CommentLike).filter_by(user_id=me.id).filter(
                CommentLike.comment_id.in_(all_ids)
            )
        }

    result = []
    for f in floors:
        floor_replies = by_parent.get(f.id, [])
        preview = floor_replies[:REPLY_PREVIEW]
        result.append(
            {
                **comment_out(f, liked=f.id in my_likes),
                "floor_number": number_map[f.id],
                "replies": [comment_out(r, liked=r.id in my_likes) for r in preview],
                "has_more_replies": len(floor_replies) > REPLY_PREVIEW,
            }
        )
    return result


@router.get("/comments/{comment_id}/replies")
def list_replies(
    comment_id: int,
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    me: Optional[User] = Depends(get_optional_user),
):
    """展开某层楼的全部楼中楼（分页）"""
    floor = db.get(Comment, comment_id)
    if floor is None or floor.deleted or floor.parent_id is not None:
        raise HTTPException(404, "楼层不存在")
    replies = (
        db.query(Comment)
        .filter_by(parent_id=comment_id, deleted=False)
        .order_by(Comment.created_at.asc())
        .offset((page - 1) * size)
        .limit(size)
        .all()
    )
    my_likes = set()
    if me is not None and replies:
        my_likes = {
            x.comment_id
            for x in db.query(CommentLike).filter_by(user_id=me.id).filter(
                CommentLike.comment_id.in_([r.id for r in replies])
            )
        }
    return [comment_out(r, liked=r.id in my_likes) for r in replies]


@router.post("/posts/{post_id}/comments")
def create_comment(post_id: int, data: CommentCreate, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    post = db.get(Post, post_id)
    if post is None or post.deleted:
        raise HTTPException(404, "帖子不存在")

    parent_id = data.parent_id
    if parent_id is not None:
        parent = db.get(Comment, parent_id)
        if parent is None or parent.deleted or parent.post_id != post_id:
            raise HTTPException(400, "回复的楼层不存在")
        if parent.parent_id is not None:
            # 楼中楼里再回复，仍挂到楼层（贴吧式：回复平铺在楼中楼里）
            parent_id = parent.parent_id
        if len(data.content) > 300:
            raise HTTPException(400, "楼中楼最多 300 字")

    comment = Comment(post_id=post_id, user_id=me.id, content=data.content, parent_id=parent_id)
    post.comment_count += 1
    if parent_id is not None:
        floor = db.get(Comment, parent_id)
        if floor:
            floor.reply_count += 1
    db.add(comment)
    db.flush()
    _notify(db, post.author_id, "comment", me.id, post_id=post_id, comment_id=comment.id)
    if parent_id is not None:
        # 回复了别人的楼层，也通知楼层作者（除非就是楼主/自己）
        floor = db.get(Comment, parent_id)
        if floor and floor.user_id != me.id:
            _notify(db, floor.user_id, "comment", me.id, post_id=post_id, comment_id=comment.id)
    db.commit()
    db.refresh(comment)
    return comment_out(comment)


@router.delete("/comments/{comment_id}")
def delete_comment(comment_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    comment = db.get(Comment, comment_id)
    if comment is None or comment.deleted:
        raise HTTPException(404, "评论不存在")
    if comment.user_id != me.id:
        raise HTTPException(403, "只能删除自己的评论")
    comment.deleted = True
    removed = 1
    if comment.parent_id is None:
        # 删除整层楼：楼中楼一并删除（贴吧行为）
        replies = db.query(Comment).filter_by(parent_id=comment.id, deleted=False).all()
        for r in replies:
            r.deleted = True
        removed += len(replies)
    else:
        floor = db.get(Comment, comment.parent_id)
        if floor:
            floor.reply_count = max(0, floor.reply_count - 1)
    post = db.get(Post, comment.post_id)
    if post:
        post.comment_count = max(0, post.comment_count - removed)
    db.commit()
    return {"ok": True}


@router.post("/comments/{comment_id}/like")
def toggle_comment_like(comment_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    comment = db.get(Comment, comment_id)
    if comment is None or comment.deleted:
        raise HTTPException(404, "评论不存在")
    like = db.query(CommentLike).filter_by(user_id=me.id, comment_id=comment_id).first()
    if like is None:
        db.add(CommentLike(user_id=me.id, comment_id=comment_id))
        comment.like_count += 1
        liked = True
    else:
        db.delete(like)
        comment.like_count = max(0, comment.like_count - 1)
        liked = False
    db.commit()
    return {"liked": liked, "like_count": comment.like_count}


# ---------- 关注 ----------
@router.post("/users/{user_id}/follow")
def toggle_follow(user_id: int, db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    if user_id == me.id:
        raise HTTPException(400, "不能关注自己")
    target = db.get(User, user_id)
    if target is None:
        raise HTTPException(404, "用户不存在")
    follow = db.query(Follow).filter_by(follower_id=me.id, following_id=user_id).first()
    if follow is None:
        db.add(Follow(follower_id=me.id, following_id=user_id))
        _notify(db, user_id, "follow", me.id)
        following = True
    else:
        db.delete(follow)
        following = False
    db.commit()
    follower_count = db.query(Follow).filter_by(following_id=user_id).count()
    return {"following": following, "follower_count": follower_count}
