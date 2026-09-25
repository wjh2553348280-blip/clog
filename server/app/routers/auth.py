from __future__ import annotations

import re

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import UserCreate, UserLogin
from ..security import create_token, hash_password, verify_password
from ..serializers import user_out

router = APIRouter(prefix="/auth", tags=["认证"])

USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]+$")


def _token_response(user: User) -> dict:
    return {"access_token": create_token(user.id), "token_type": "bearer", "user": user_out(user)}


@router.post("/register")
def register(data: UserCreate, db: Session = Depends(get_db)):
    if not USERNAME_RE.match(data.username):
        raise HTTPException(400, "用户名只能包含字母、数字和下划线")
    if db.query(User).filter_by(username=data.username).first():
        raise HTTPException(400, "用户名已被注册")
    user = User(
        username=data.username,
        password_hash=hash_password(data.password),
        nickname=data.nickname,
        avatar_emoji=data.avatar_emoji,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return _token_response(user)


@router.post("/login")
def login(data: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter_by(username=data.username).first()
    if user is None or not verify_password(data.password, user.password_hash):
        raise HTTPException(401, "用户名或密码错误")
    return _token_response(user)
