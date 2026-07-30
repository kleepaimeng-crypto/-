# 前中后舱网联数据显示平台 Ubuntu 远程部署指南（简化版）

本文仅保留熟练部署时需要的操作，采用推荐的 Docker Compose 全栈部署方式。默认项目目录为 `/opt/cabin-data-platform`，页面端口为 `18080`。

## 1. 首次部署

### 1.1 安装 Docker

```bash
sudo apt update
sudo apt install -y git curl
curl -fsSL https://get.docker.com | sudo bash
sudo usermod -aG docker $USER
```

执行后重新登录 SSH，使 Docker 用户组生效。

### 1.2 获取项目

```bash
sudo mkdir -p /opt/cabin-data-platform
sudo chown -R $USER:$USER /opt/cabin-data-platform
git clone https://github.com/kleepaimeng-crypto/-.git /opt/cabin-data-platform
cd /opt/cabin-data-platform
```

如果项目通过 SFTP、堡垒机等方式上传，只需确保 `backend`、`frontend`、`deploy` 和 `.env.example` 位于该目录。

### 1.3 配置环境变量

```bash
cd /opt/cabin-data-platform
cp .env.example .env
openssl rand -base64 48
nano .env
```

至少修改以下三项，正式环境不要使用示例密码：

```env
POSTGRES_PASSWORD=数据库强密码
JWT_SECRET=上一步生成的随机字符串
BOOTSTRAP_ADMIN_PASSWORD=管理员强密码
```

其他配置保持默认即可。常用可选项如下：

```env
POSTGRES_DB=cabin_data_platform
POSTGRES_USER=postgres
HTTP_PORT=18080
BACKEND_PORT=18081
UDP_ENABLED=true
APP_ZONE_ID=Asia/Shanghai
```

### 1.4 启动

```bash
cd /opt/cabin-data-platform
docker compose -p cabin-data-platform --env-file .env \
  -f deploy/docker-compose.yml up -d --build
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

首次构建需要下载依赖，耗时通常较长。服务启动后访问：

```text
http://服务器IP:18080/
```

### 1.5 放行端口

服务器启用 UFW 时执行：

```bash
sudo ufw allow 22/tcp
sudo ufw allow 18080/tcp
sudo ufw allow 8190:8196/udp
```

如果不接收外部 UDP 数据，可以省略最后一条。数据库 `15432` 和后端 `18081` 只绑定本机，不需要对外放行。

## 2. 更新部署

```bash
cd /opt/cabin-data-platform
git pull
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

数据库保存在 Docker 卷中，重新构建不会删除数据。

## 3. 常用维护命令

```bash
cd /opt/cabin-data-platform

# 查看状态
docker compose --env-file .env -f deploy/docker-compose.yml ps

# 查看日志
docker compose --env-file .env -f deploy/docker-compose.yml logs -f
docker compose --env-file .env -f deploy/docker-compose.yml logs -f backend

# 重启后端
docker compose --env-file .env -f deploy/docker-compose.yml restart backend

# 停止全部服务
docker compose --env-file .env -f deploy/docker-compose.yml down
```

不要执行 `docker compose down -v`，否则会删除 PostgreSQL 数据卷。

页面无法访问或容器未正常启动时，只需先查看状态和最近日志：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml ps
docker compose --env-file .env -f deploy/docker-compose.yml \
  logs --tail=200 postgres backend frontend
```

## 4. 离线服务器部署

目标服务器无法访问 Docker Hub、Maven 或 npm 仓库时，在一台可联网且与服务器 CPU 架构一致的电脑上构建并导出镜像。

联网电脑执行：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml build backend frontend
docker pull postgres:18
docker save -o cabin-platform-images.tar \
  postgres:18 \
  cabin-data-platform-backend:latest \
  cabin-data-platform-frontend:latest
```

将镜像包和项目文件上传到服务器；项目至少保留 `deploy`、`backend`、`frontend` 和 `.env.example`。然后执行：

```bash
docker load -i cabin-platform-images.tar

cd /opt/cabin-data-platform
cp .env.example .env
nano .env

docker compose --env-file .env -f deploy/docker-compose.yml \
  up -d --no-build --pull never
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

离线更新时导入新镜像，然后执行：

```bash
cd /opt/cabin-data-platform
docker compose --env-file .env -f deploy/docker-compose.yml \
  up -d --no-build --pull never --force-recreate backend frontend
```

## 5. 关键说明

- 前端、后端和 PostgreSQL 均由 `deploy/docker-compose.yml` 管理，宿主机不需要安装 Java、Node.js 或 Nginx。
- GeoJSON 地图已包含在前端源码和前端镜像中，不需要单独上传。
- 前端访问端口为 `18080/tcp`，外部 UDP 数据入口为 `8190～8196/udp`。
- `.env` 包含密码和密钥，不要提交到 Git 或发送给无关人员。
- 需要传统 systemd、宿主机 Nginx、详细排查或架构说明时，查阅完整版《Ubuntu 远程服务器部署指南》。
