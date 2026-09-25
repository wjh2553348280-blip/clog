import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# SQLite 本地开发用；部署时换成 PostgreSQL 连接串即可
DATABASE_URL = os.environ.get("CLOG_DATABASE_URL", "sqlite:///" + str(BASE_DIR / "clog.db"))

# JWT 密钥：生产环境通过环境变量 CLOG_SECRET_KEY 注入，本地开发用默认值即可
SECRET_KEY = os.environ.get("CLOG_SECRET_KEY", "clog-dev-secret-change-me-before-deploy")
ALGORITHM = "HS256"
TOKEN_EXPIRE_DAYS = 30

# 上传文件目录，通过 /static/... 访问
UPLOAD_DIR = BASE_DIR / "uploads"
UPLOAD_DIR.mkdir(exist_ok=True)
STATIC_URL_PREFIX = "/static"

# 上传限制
MAX_IMAGE_SIZE = 10 * 1024 * 1024   # 10MB
MAX_VIDEO_SIZE = 60 * 1024 * 1024   # 60MB
