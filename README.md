# clog · 书房

一个安静的文字社交社区。发短帖、盖楼中楼、看关注流——没有算法轰炸，只有「纸墨灯花」的留白。

> **clog** = compose + log。设计使命是一间**书房**：让用户在冥想或思考的状态里，随手记录想法，也读到别人的想法。

```
┌──────────────┐   HTTP (Retrofit)   ┌─────────────────────┐
│  Android App │ ◄──────────────────► │  FastAPI 后端        │
│  Compose UI  │   JSON + JWT Bearer │  SQLAlchemy + SQLite │
└──────────────┘                     └─────────────────────┘
```

## 功能特性

**社区与内容**
- 官方预置 6 个社区：学习笔记 / 技术踩坑 / 校园日常 / 生活随笔 / 吃喝玩乐 / 树洞
- 短帖：标题（≤20 字）+ 正文（≤2000 字）+ 最多 9 张图片 / 视频，流式上传
- 楼中楼评论：楼层 + 嵌套回复、只看楼主、最新/热评排序、楼层身份标识、楼中楼限 300 字
- 点赞 / 收藏 / 关注 / 举报，透明度化热度排序（`(赞×2+评×5+藏×3−举报×5)/(小时+2)^1.5`）
- 搜索（帖子/用户）、通知系统、浏览历史（首次浏览才计 view_count）

**交互体验**
- 发现页「推荐 / 关注」双流切换，下拉刷新
- 页面栈导航：任何页面返回都回到上一页，支持系统返回键与手势
- 自绘推拉侧边栏（graphicsLayer 平移，最坏帧 28ms），收藏 / 浏览历史入口
- 点赞、收藏、关注全部乐观更新，失败自动回滚
- 骨架屏、空态与错误态组件、旋转屏状态保持（rememberSaveable + GsonSavers）

**设计语言：纸墨灯花**
| 角色 | 色值 | 用途 |
|---|---|---|
| 纸 Paper | `#F6F3EC` | 主背景，浅色底 |
| 墨 Ink | `#35312B` | 文字、图标、底栏 |
| 灯 Lamp | `#D97757` | 赤陶灯色，仅用于选中态与强调 |
| 花 Sage | `#7C8B6B` | 鼠尾草绿，次要点缀 |
| 深夜书房 | `#26231F` | 深色模式底 |

标题衬线体（Noto Serif）、正文行距放宽、字母头像（藏书章风）、克制的动效。**没有**花哨特效、没有装饰性符号，界面要素越少越好。

## 技术栈

**后端 `server/`** — Python 3.9+
- FastAPI + Uvicorn：REST API、自动文档（`/docs`）
- SQLAlchemy ORM + SQLite（开发）；启动时轻量 `ALTER TABLE` 迁移，可平滑换 PostgreSQL
- PyJWT + bcrypt：无状态 JWT 认证，密码哈希存储
- python-multipart + 静态文件：图片/视频上传，`/static/...` 访问

**安卓端 `android/`** — Kotlin 2.2 + Jetpack Compose
- 单 Activity + 页面栈导航（sealed class + listSaver），覆盖层按栈顺序渲染
- Retrofit + Gson（自定义 RequestBody 64KB 流式上传）、Coil 3.4（按目标宽度下采样）
- Media3/ExoPlayer 视频播放、DataStore 持久化登录态
- Material3 极简改造：自绘侧边栏、FloatingActionButton 发布、Outlined 图标

## 项目结构

```
clog/
├── server/                 # FastAPI 后端
│   ├── app/
│   │   ├── main.py         # 入口：预置社区、轻量迁移、CORS
│   │   ├── models.py       # SQLAlchemy 模型（User/Post/Comment/...）
│   │   ├── schemas.py      # Pydantic 请求/响应模型
│   │   ├── security.py     # JWT 签发与校验
│   │   └── routers/        # auth / users / posts / interactions
│   │                       #   / feed / notifications / search
│   └── requirements.txt
└── android/                # Jetpack Compose 应用
    └── app/src/main/java/com/example/clog/
        ├── data/           # Retrofit API、Models、DataStore
        └── ui/             # 各页面 + 设计系统（theme/、components.kt）
```

## 快速开始

### 后端

```bash
cd server
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
./venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
```

访问 `http://127.0.0.1:8000/docs` 查看自动生成的接口文档。首次启动自动建表并预置 6 个社区。

### 安卓端

1. 用 Android Studio 打开 `android/` 目录
2. 真机联调时让手机访问电脑上的后端：

```bash
adb reverse tcp:8000 tcp:8000   # USB 重插后需重新执行
```

3. 运行到设备。开屏 → 注册 / 登录 → 发帖。

生产环境部署时，修改 `ApiClient.kt` 的 `BASE_URL` 并移除 `usesCleartextTraffic`。

## API 概览

| 模块 | 端点 |
|---|---|
| 认证 | `POST /auth/register` `POST /auth/login` |
| 帖子 | `POST /posts` `GET /posts/{id}` `DELETE /posts/{id}` `POST /posts/{id}/report` |
| 媒体 | `POST /upload/media`（流式，图片 10MB / 视频 60MB） |
| 评论 | `POST /posts/{id}/comments` `GET /posts/{id}/comments`（`sort=hot\|new`、`author_only`、服务端楼号）`GET /comments/{id}/replies` `POST /comments/{id}/like` |
| 互动 | `POST /posts/{id}/like` `POST /posts/{id}/favorite` `POST /users/{id}/follow` |
| 信息流 | `GET /feed`（推荐，透明热度公式）`GET /feed/following` `GET /hot`（24h 热榜）`GET /feed/community/{id}` |
| 用户 | `GET /users/{id}` `PUT /users/me` `GET /users/{id}/posts\|favorites\|followers\|following` `GET /users/me/history` |
| 其他 | `GET /communities` `GET /search` `GET /notifications` `POST /notifications/read-all` `GET /health` |

## 设计决策记录

- **短帖为主**：参考 X / 小红书形态，而非长文博客
- **楼中楼正序**：默认最新在前（热评置顶会打乱楼中楼阅读流），热榜公式公开以建立信任
- **去装饰化**：不做热榜 Tab 混排、不显示社区标签、无装饰性图标——「界面要素越少越好」
- **覆盖层导航**：返回不刷新列表，靠页面栈保证任意路径可回退

## 后续规划

- [ ] 部署上线：学生服务器 + nginx + HTTPS
- [ ] 端到端加密私信（X25519 + AES-GCM）
- [ ] strings.xml 多语言抽取、动态字体无障碍
