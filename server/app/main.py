from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy import inspect, text

from .config import STATIC_URL_PREFIX, UPLOAD_DIR
from .database import Base, SessionLocal, engine
from . import models  # noqa: F401  确保模型注册到 Base
from .routers import auth, feed, interactions, notifications, posts, search, users

# 官方预置社区（PDF 决策 2：官方预置分类）
PRESET_COMMUNITIES = [
    ("学习笔记", "study", "学到的知识和整理", "📚"),
    ("技术踩坑", "tech", "遇到的技术问题和解决办法", "🔧"),
    ("校园日常", "campus", "校园生活碎碎念", "🎓"),
    ("生活随笔", "life", "生活里的瞬间", "🌿"),
    ("吃喝玩乐", "fun", "好吃的好玩的", "🍜"),
    ("树洞", "treehole", "心里话都可以说", "🌙"),
]


def _migrate(db) -> None:
    """轻量迁移：给已有库补新列（create_all 不会改旧表）"""
    inspector = inspect(engine)
    if "posts" in inspector.get_table_names():
        cols = [c["name"] for c in inspector.get_columns("posts")]
        if "view_count" not in cols:
            db.execute(text("ALTER TABLE posts ADD COLUMN view_count INTEGER DEFAULT 0"))
        if "title" not in cols:
            db.execute(text("ALTER TABLE posts ADD COLUMN title VARCHAR(100) DEFAULT ''"))
    if "comments" in inspector.get_table_names():
        ccols = [c["name"] for c in inspector.get_columns("comments")]
        if "parent_id" not in ccols:
            db.execute(text("ALTER TABLE comments ADD COLUMN parent_id INTEGER"))
        if "reply_count" not in ccols:
            db.execute(text("ALTER TABLE comments ADD COLUMN reply_count INTEGER DEFAULT 0"))
    db.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        _migrate(db)
        for name, slug, description, icon in PRESET_COMMUNITIES:
            if db.query(models.Community).filter_by(slug=slug).first() is None:
                db.add(models.Community(name=name, slug=slug, description=description, icon=icon))
        db.commit()
    finally:
        db.close()
    yield


app = FastAPI(title="clog API", description="clog 文字社交社区的 REST API", version="0.1.0", lifespan=lifespan)

# 开发阶段放开跨域；上线后收紧
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 上传的图片/视频通过 /static/xxx 访问
app.mount(STATIC_URL_PREFIX, StaticFiles(directory=str(UPLOAD_DIR)), name="static")

for r in (auth, users, posts, interactions, feed, notifications, search):
    app.include_router(r.router)


@app.get("/health")
def health():
    return {"ok": True, "app": "clog", "version": "0.1.0"}
