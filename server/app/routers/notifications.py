from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import get_current_user
from ..models import Notification, Post, User
from ..serializers import post_out, user_out

router = APIRouter(prefix="/notifications", tags=["通知"])


@router.get("")
def list_notifications(db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    items = (
        db.query(Notification)
        .filter_by(user_id=me.id)
        .order_by(Notification.created_at.desc())
        .limit(50)
        .all()
    )
    unread = db.query(Notification).filter_by(user_id=me.id, read=False).count()
    result = []
    for n in items:
        item = {
            "id": n.id,
            "type": n.type,
            "read": n.read,
            "created_at": n.created_at,
            "actor": user_out(n.actor),
            "post_id": n.post_id,
            "comment_id": n.comment_id,
        }
        # 带上帖子内容片段，客户端可以直接跳转
        if n.post_id:
            post = db.get(Post, n.post_id)
            if post and not post.deleted:
                item["post_snippet"] = post.content[:60]
        result.append(item)
    return {"items": result, "unread": unread}


@router.post("/read-all")
def read_all(db: Session = Depends(get_db), me: User = Depends(get_current_user)):
    db.query(Notification).filter_by(user_id=me.id, read=False).update({"read": True})
    db.commit()
    return {"ok": True}
