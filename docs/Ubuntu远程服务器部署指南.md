# 前中后舱网联数据显示平台 Ubuntu 远程部署指南

本文档说明如何将本项目部署到远程 Ubuntu 服务器。

当前推荐使用全栈 Docker Compose 部署。一条命令会启动：

- `postgres`：PostgreSQL 18 数据库。
- `backend`：Java 21 / Spring Boot 后端，启动时自动执行 Flyway 数据库迁移。
- `frontend`：Nginx 托管 Vue/Vite 构建产物，代理 `/api/v1`，并直接提供构建产物中的 `/map/geojson` 行政区地图数据。

如果服务器不能使用 Docker 构建镜像，也可以使用本文第 5～8 节的传统方式：后端由 `systemd` 运行，前端由宿主机 Nginx 托管。

## 1. 服务器环境准备

以下命令适用于 Ubuntu 22.04 / 24.04。

```bash
sudo apt update
sudo apt install -y git curl unzip rsync
```

安装 Docker 和 Docker Compose：

```bash
curl -fsSL https://get.docker.com | sudo bash
sudo usermod -aG docker $USER
```

重新登录 SSH 后验证：

```bash
docker -v
docker compose version
```

如果使用推荐的全栈 Docker Compose 部署，到这里环境准备已经完成，不需要在宿主机安装 Java、Node.js 或 Nginx。

仅传统部署方式需要安装 Java 21：

```bash
sudo apt install -y openjdk-21-jdk
java -version
```

仅传统部署方式需要安装 Node.js 22 和 Nginx。前端 `package.json` 要求 `node >= 22.12.0`：

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
sudo apt install -y nginx
node -v
npm -v
```

## 2. 拉取项目代码

推荐部署到 `/opt/cabin-data-platform`：

```bash
sudo mkdir -p /opt/cabin-data-platform
sudo chown -R $USER:$USER /opt/cabin-data-platform

cd /opt/cabin-data-platform
git clone https://github.com/kleepaimeng-crypto/-.git /opt/cabin-data-platform
```

如果是手动上传代码，也需要保证目录结构类似：

```text
/opt/cabin-data-platform
├── backend
├── frontend
├── deploy
├── simulator
└── .env.example
```

## 3. 配置数据库和后端环境变量

`deploy/docker-compose.yml` 明确要求必须提供 `POSTGRES_PASSWORD`、`JWT_SECRET` 和 `BOOTSTRAP_ADMIN_PASSWORD`，缺少任何一项时都不会启动。

在项目根目录创建 `.env`：

```bash
cd /opt/cabin-data-platform
cp .env.example .env
nano .env
```

推荐配置如下：

```env
POSTGRES_DB=cabin_data_platform
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123456
POSTGRES_PORT=15432
POSTGRES_IMAGE=postgres:18

SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:15432/cabin_data_platform
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=123456

JWT_ISSUER=cabin-data-platform
JWT_SECRET=bNdpeBjIsc0F29r/XhG3r0XNMJpld/2eid7aMvUk1l51eOSKgEheIVH4ms5G3vRi
JWT_EXPIRES_IN_SECONDS=7200

BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=123456
BOOTSTRAP_ADMIN_EMAIL=admin@example.com

UDP_ENABLED=true
APP_ZONE_ID=Asia/Shanghai

HTTP_PORT=18080
BACKEND_PORT=18081

BACKEND_IMAGE=cabin-data-platform-backend:latest
FRONTEND_IMAGE=cabin-data-platform-frontend:latest
```

数据库账号已统一为 `postgres`，数据库密码统一为 `123456`。这与当前项目 `.env` 的写法一致。`123456` 只适合内网测试环境；公网正式环境应改成强密码，并同步修改 `POSTGRES_PASSWORD` 和 `SPRING_DATASOURCE_PASSWORD`。

可以使用以下命令生成 `JWT_SECRET`：

```bash
openssl rand -base64 48
```

## 4. 使用 Docker Compose 一键启动整个项目（推荐）

### 4.1 确认 GeoJSON 地图文件

当前地图使用 `frontend/public/map/geojson`，不再使用 `frontend/map/tiles_street` 瓦片。Vite 构建时会自动把 `public` 目录复制到 `dist`，因此 GeoJSON 会直接进入前端镜像，不需要单独上传或挂载。

```text
/opt/cabin-data-platform/frontend/public/map/geojson
├── manifest.json
├── china.json
├── province-boundaries.json
├── province
└── citys
```

构建前检查清单文件：

```bash
cd /opt/cabin-data-platform
test -f frontend/public/map/geojson/manifest.json && echo "GeoJSON 地图文件正常"
```

如果文件不存在，说明上传的项目代码不完整，需要重新上传或拉取 `frontend/public/map/geojson`。这些文件属于前端源码的一部分，不需要额外的地图压缩包。

### 4.2 构建并启动全部服务

在项目根目录执行：

```bash
cd /opt/cabin-data-platform
docker compose -p cabin-data-platform --env-file .env -f deploy/docker-compose.yml up -d --build
```

此命令会依次完成：

1. 启动 PostgreSQL 并等待数据库健康检查通过。
2. 构建并启动后端，连接 `postgres:5432`，自动执行 Flyway 迁移。
3. 等待后端健康检查通过。
4. 构建前端并启动 Nginx，通过服务器 `18080` 端口提供页面。

查看三个服务的状态：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

正常情况下，`postgres` 和 `backend` 会显示 `healthy`，`frontend` 会显示 `Up`。

查看启动日志：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml logs -f
```

按 `Ctrl+C` 只会退出日志查看，不会停止容器。

验证数据库连接：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres \
  psql -U postgres -d cabin_data_platform -c "\dt"
```

验证后端和前端：

```bash
curl http://127.0.0.1:18081/actuator/health
curl -I http://127.0.0.1:18080/
curl -I http://127.0.0.1:18080/map/geojson/manifest.json
```

浏览器访问：

```text
http://服务器IP:18080/
```

### 4.3 Compose 中的端口和数据

- `18080/tcp`：前端页面，同时代理后端 API，可通过服务器 IP 直接访问。
- `127.0.0.1:18081/tcp`：后端健康检查和本机调试，不直接暴露到公网。
- `127.0.0.1:15432/tcp`：PostgreSQL 本机调试端口，不直接暴露到公网；容器之间仍使用 `postgres:5432`。
- `8190-8196/udp`：外部设备或模拟器入口，映射到后端容器的 `8090-8096/udp`。
- `postgres_data`：数据库持久化卷，重新构建容器不会丢失数据。
- `frontend/public/map/geojson`：在构建前端镜像时自动复制到 `/usr/share/nginx/html/map/geojson`，不使用宿主机目录挂载。

Compose 文件已经固定项目名为 `cabin-data-platform`。启动命令仍显式使用 `-p cabin-data-platform`，避免与服务器现有的 `docker` Compose 项目混淆。

### 4.4 内网或完全离线服务器部署

如果目标服务器不能访问 Docker Hub、Maven 仓库和 npm 仓库，不要在目标服务器执行 `--build`。推荐在能联网的电脑上构建最终运行镜像，导出后传入内网。目标服务器只负责 `docker load` 和启动容器。

最终运行只需要以下三个镜像：

```text
postgres:18
cabin-data-platform-backend:latest
cabin-data-platform-frontend:latest
```

Maven、Node、JDK 和 Nginx 的基础镜像都只用于联网电脑上的构建过程，不需要单独传到内网服务器。

#### 4.4.1 确认服务器 CPU 架构

先在内网服务器执行：

```bash
uname -m
```

常见对应关系：

```text
x86_64  -> linux/amd64
aarch64 -> linux/arm64
```

联网电脑构建的镜像架构必须与服务器一致。以下示例以常见的 `linux/amd64` 为例。

#### 4.4.2 在能联网的 Windows 电脑上构建镜像

打开 PowerShell，进入项目根目录。确保根目录 `.env` 已按第 3 节补齐必填变量，然后执行：

```powershell
cd D:\你的项目目录\前中后舱网联数据显示平台

$env:DOCKER_DEFAULT_PLATFORM = "linux/amd64"

docker compose --env-file .env -f deploy\docker-compose.yml build backend frontend
docker pull postgres:18
```

确认镜像存在并检查架构：

```powershell
docker image inspect postgres:18 --format "{{.Os}}/{{.Architecture}}"
docker image inspect cabin-data-platform-backend:latest --format "{{.Os}}/{{.Architecture}}"
docker image inspect cabin-data-platform-frontend:latest --format "{{.Os}}/{{.Architecture}}"
```

三个命令都应输出：

```text
linux/amd64
```

将三个最终镜像打成一个离线包：

```powershell
docker save -o cabin-platform-images-linux-amd64.tar `
  postgres:18 `
  cabin-data-platform-backend:latest `
  cabin-data-platform-frontend:latest
```

记录文件校验值：

```powershell
Get-FileHash .\cabin-platform-images-linux-amd64.tar -Algorithm SHA256
```

镜像包可能比较大，属于正常现象。

如果联网构建机也是 Linux，使用：

```bash
export DOCKER_DEFAULT_PLATFORM=linux/amd64
docker compose --env-file .env -f deploy/docker-compose.yml build backend frontend
docker pull postgres:18
docker save -o cabin-platform-images-linux-amd64.tar \
  postgres:18 \
  cabin-data-platform-backend:latest \
  cabin-data-platform-frontend:latest
sha256sum cabin-platform-images-linux-amd64.tar
```

#### 4.4.3 打包项目文件

除了镜像包，还需要把以下内容传到服务器：

- 项目中的 `deploy/docker-compose.yml`。
- 项目根目录 `.env.example`，到服务器后复制为 `.env`。

推荐直接上传项目目录，但可以删除或排除以下不需要的本地构建缓存：

```text
backend/target
frontend/node_modules
frontend/dist
.git
.idea
```

GeoJSON 地图已经位于 `frontend/public/map/geojson`，并已包含在构建好的前端镜像中，不需要单独制作或上传地图压缩包。

最终需要传入内网的主要文件为：

```text
cabin-platform-images-linux-amd64.tar
项目代码（至少包含 deploy、backend、frontend 和 .env.example）
```

可以通过移动硬盘、堡垒机、SFTP 或内网文件服务器传输。

#### 4.4.4 在内网 Ubuntu 服务器导入

假设镜像包上传到 `/opt/offline-packages`，项目上传到 `/opt/cabin-data-platform`：

```bash
cd /opt/offline-packages
sha256sum cabin-platform-images-linux-amd64.tar
docker load -i cabin-platform-images-linux-amd64.tar
```

将 `sha256sum` 输出与联网电脑记录的 SHA256 对比，必须完全一致。

确认三个镜像均已导入：

```bash
docker image ls
docker image inspect postgres:18 --format '{{.Os}}/{{.Architecture}}'
docker image inspect cabin-data-platform-backend:latest --format '{{.Os}}/{{.Architecture}}'
docker image inspect cabin-data-platform-frontend:latest --format '{{.Os}}/{{.Architecture}}'
```

如果服务器保留完整源码，可以检查 GeoJSON 源文件：

```bash
test -f /opt/cabin-data-platform/frontend/public/map/geojson/manifest.json \
  && echo "GeoJSON 地图源文件正常"
```

使用预构建前端镜像启动时，GeoJSON 已在镜像内部，即使服务器不单独保存地图目录也能正常运行。

创建并检查环境变量：

```bash
cd /opt/cabin-data-platform
cp .env.example .env
nano .env
```

按照第 3 节设置数据库密码、JWT 密钥和管理员密码。

#### 4.4.5 禁止拉取并离线启动

在内网服务器执行：

```bash
cd /opt/cabin-data-platform
docker compose --env-file .env -f deploy/docker-compose.yml \
  up -d --no-build --pull never
```

这里必须保留：

- `--no-build`：禁止在服务器上构建，避免访问 Maven 和 npm 仓库。
- `--pull never`：禁止尝试从 Docker Hub 或其他仓库拉取镜像。

如果有任何镜像没有成功导入，命令会直接报出缺少的镜像名称，不会静默访问公网。

启动后验证：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml ps
curl http://127.0.0.1:18081/actuator/health
curl -I http://127.0.0.1:18080/
curl -I http://127.0.0.1:18080/map/geojson/manifest.json
```

#### 4.4.6 后续离线更新

代码更新后，在联网电脑重新构建并导出相同名称的后端、前端镜像。把新镜像包传入服务器并再次执行 `docker load`，然后执行：

```bash
cd /opt/cabin-data-platform
docker compose --env-file .env -f deploy/docker-compose.yml \
  up -d --no-build --pull never --force-recreate backend frontend
```

PostgreSQL 数据保存在 `postgres_data` 卷中，重新创建后端和前端容器不会删除数据库。

#### 4.4.7 可以访问内网镜像仓库时

如果单位内网已经部署 Harbor、Nexus 等镜像仓库，可以省去 tar 文件传输。将三个镜像推到内网仓库，并在服务器 `.env` 中设置：

```env
POSTGRES_IMAGE=harbor.example.internal/cabin/postgres:18
BACKEND_IMAGE=harbor.example.internal/cabin/backend:latest
FRONTEND_IMAGE=harbor.example.internal/cabin/frontend:latest
```

然后使用普通 Compose 命令启动。服务器只需能够访问内网镜像仓库，不需要访问公网。

> 如果内网服务器尚未安装 Docker Engine 和 Compose 插件，还需要在同版本、同架构的 Ubuntu 联网机器上提前下载对应的 `.deb` 包及依赖，或使用单位内部 APT 软件源。镜像离线包不能代替 Docker 本身。

> 以下第 5～8 节是传统部署方式。已经使用全栈 Compose 部署的服务器可以直接跳到第 9 节。
>
> 当前目标服务器的 `80/tcp`、`8080/tcp` 和 `8090/udp` 已被现有 Aviation 项目占用，因此不要在该服务器使用未调整端口的传统部署方式。

## 5. 传统方式：构建后端

传统方式仍使用 Compose，但只启动 PostgreSQL：

```bash
cd /opt/cabin-data-platform
docker compose --env-file .env -f deploy/docker-compose.yml up -d postgres
```

```bash
cd /opt/cabin-data-platform/backend
chmod +x mvnw
./mvnw clean package -DskipTests
```

构建完成后查看 jar 包：

```bash
ls -lh target/*.jar
```

创建后端部署目录：

```bash
sudo mkdir -p /opt/cabin-backend
sudo cp target/*.jar /opt/cabin-backend/app.jar
sudo chown -R $USER:$USER /opt/cabin-backend
```

## 6. 传统方式：配置后端 systemd 服务

创建后端环境变量文件：

```bash
sudo nano /etc/cabin-backend.env
```

写入以下内容：

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:15432/cabin_data_platform
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=123456

JWT_ISSUER=cabin-data-platform
JWT_SECRET=你的至少32位随机字符串
JWT_EXPIRES_IN_SECONDS=7200

BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=你的管理员初始密码
BOOTSTRAP_ADMIN_EMAIL=admin@example.com

UDP_ENABLED=true
APP_ZONE_ID=Asia/Shanghai
MANAGEMENT_HEALTH_REDIS_ENABLED=false
```

创建 systemd 服务：

```bash
sudo nano /etc/systemd/system/cabin-backend.service
```

写入：

```ini
[Unit]
Description=Cabin Data Platform Backend
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=/opt/cabin-backend
EnvironmentFile=/etc/cabin-backend.env
ExecStart=/usr/bin/java -jar /opt/cabin-backend/app.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动后端：

```bash
sudo systemctl daemon-reload
sudo systemctl enable cabin-backend
sudo systemctl start cabin-backend
```

查看日志：

```bash
sudo journalctl -u cabin-backend -f
```

验证后端健康状态：

```bash
curl http://127.0.0.1:8080/actuator/health
```

注意：项目后端默认 profile 是 `dev`，部署时必须设置 `SPRING_PROFILES_ACTIVE=prod`，否则会使用开发配置连接默认测试库。

## 7. 传统方式：构建前端

```bash
cd /opt/cabin-data-platform/frontend
cp .env.example .env.production
nano .env.production
```

生产环境建议配置：

```env
VITE_API_BASE_URL=/api/v1
VITE_MAP_MIN_ZOOM=3
VITE_MAP_MAX_ZOOM=10
```

安装依赖并构建：

```bash
npm ci
npm run build
```

部署前端静态文件：

```bash
sudo mkdir -p /var/www/cabin-platform
sudo rm -rf /var/www/cabin-platform/*
sudo cp -r dist/* /var/www/cabin-platform/
sudo chown -R www-data:www-data /var/www/cabin-platform
```

## 8. 传统方式：配置 GeoJSON 地图和 Nginx

### 8.1 验证构建产物中的 GeoJSON

`frontend/public/map/geojson` 属于前端公共资源。执行 `npm run build` 后，Vite 会自动生成：

```text
frontend/dist/map/geojson/manifest.json
frontend/dist/map/geojson/china.json
frontend/dist/map/geojson/province-boundaries.json
frontend/dist/map/geojson/province/...
frontend/dist/map/geojson/citys/...
```

构建后验证：

```bash
cd /opt/cabin-data-platform/frontend
test -f dist/map/geojson/manifest.json \
  && echo "GeoJSON 已进入前端构建产物" \
  || echo "GeoJSON 缺失，请检查 frontend/public/map/geojson"
```

第 7 节复制整个 `dist` 目录时，GeoJSON 会一起复制到 `/var/www/cabin-platform/map/geojson`，不需要单独上传或同步地图目录。

### 8.2 创建 Nginx 站点配置

创建 Nginx 站点配置：

```bash
sudo nano /etc/nginx/sites-available/cabin-platform
```

写入以下内容，将 `server_name` 改为实际域名或服务器 IP：

```nginx
server {
    listen 80;
    server_name 你的域名或服务器IP;

    root /var/www/cabin-platform;
    index index.html;

    client_max_body_size 100m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080/api/v1/;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
        proxy_set_header Host $host;
    }

    # GeoJSON 是前端 dist 中的普通静态资源。
    # 缺失时直接返回 404，避免回退到 index.html。
    location ^~ /map/geojson/ {
        try_files $uri =404;
        access_log off;
    }
}
```

GeoJSON 请求与文件路径对应关系：

```text
浏览器请求：
/map/geojson/manifest.json

服务器文件：
/var/www/cabin-platform/map/geojson/manifest.json
```

### 8.3 启用并验证 Nginx

启用站点。如果系统中还保留 Nginx 默认站点，先取消默认站点，避免请求被默认配置接管：

```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sfn /etc/nginx/sites-available/cabin-platform /etc/nginx/sites-enabled/cabin-platform
sudo nginx -t
sudo systemctl reload nginx
```

验证首页、API 代理和 GeoJSON：

```bash
curl -I http://127.0.0.1/
curl http://127.0.0.1/actuator/health
curl -I http://127.0.0.1/map/geojson/manifest.json
```

最后一个命令应返回 `HTTP/1.1 200 OK` 和 JSON 内容类型。如果返回 `404`，依次检查：

```bash
ls -lh /var/www/cabin-platform/map/geojson/manifest.json
sudo nginx -T | grep -A 5 '/map/geojson'
sudo tail -n 100 /var/log/nginx/error.log
```

浏览器访问：

```text
http://你的服务器IP/
```

## 9. 防火墙配置

当前服务器选择 Compose 直接通过 `18080` 访问。先查看现有防火墙状态：

```bash
sudo ufw status verbose
```

确认现有规则无误后，放行 SSH、新项目前端和新项目 UDP 入口：

```bash
sudo ufw allow 22/tcp
sudo ufw allow 18080/tcp
sudo ufw allow 8190:8196/udp
```

不需要放行下面两个端口：

```text
127.0.0.1:18081  后端本机调试端口
127.0.0.1:15432  PostgreSQL 本机调试端口
```

它们已经在 Compose 中绑定到回环地址，外部机器无法直接访问。服务器原有的 `80/tcp` 和 `8088/tcp` 继续由现有项目使用，不要覆盖原有 Nginx 配置。

## 10. 可选：运行数据模拟器

安装 Python：

```bash
sudo apt install -y python3 python3-venv
```

发送一轮测试数据：

```bash
cd /opt/cabin-data-platform/simulator
python3 run_simulator.py --once --summary
```

持续发送测试数据：

```bash
python3 run_simulator.py --config simulator_config.example.json --summary
```

如果模拟器需要把 UDP 数据发送给 Compose 中的后端，除了检查 `udpHost`，还需要把目标端口从容器内部端口 `8090～8096` 改成宿主机入口 `8190～8196`。

建议复制一份服务器专用配置，不要直接覆盖示例文件：

```bash
cd /opt/cabin-data-platform/simulator
cp simulator_config.example.json simulator_config.server.json
nano simulator_config.server.json
```

同机部署时，服务器专用配置应包含：

```json
{
  "udpHost": "127.0.0.1",
  "ports": {
    "qar.frame": 8190,
    "ground.task": 8191,
    "ground.traffic_record": 8192,
    "ground.session_summary": 8193,
    "smart_window.status": 8194,
    "ife_633.behavior": 8195,
    "ife_cockrell.behavior": 8196
  }
}
```

运行服务器专用配置：

```bash
python3 run_simulator.py --config simulator_config.server.json --summary
```

跨机器部署时，将 `udpHost` 改为服务器 IP，端口仍使用 `8190～8196`，并确认服务器防火墙已放行这些 UDP 端口。

## 11. 常用维护命令

### 11.1 全栈 Compose 部署

查看全部服务状态：

```bash
docker compose --env-file /opt/cabin-data-platform/.env -f /opt/cabin-data-platform/deploy/docker-compose.yml ps
```

查看全部日志或单个服务日志：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml logs -f
docker compose --env-file .env -f deploy/docker-compose.yml logs -f backend
```

重启后端：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml restart backend
```

拉取代码并重新构建整个项目：

```bash
cd /opt/cabin-data-platform
git pull
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

停止全部服务：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down
```

`down` 不会删除 `postgres_data` 数据卷。不要在正式环境随意添加 `-v`，因为 `down -v` 会删除数据库数据。

### 11.2 传统 systemd + Nginx 部署

重启并查看后端日志：

```bash
sudo systemctl restart cabin-backend
sudo journalctl -u cabin-backend -n 200 --no-pager
sudo journalctl -u cabin-backend -f
```

重新部署后端：

```bash
cd /opt/cabin-data-platform
git pull

cd backend
./mvnw clean package -DskipTests

sudo systemctl stop cabin-backend
sudo cp target/*.jar /opt/cabin-backend/app.jar
sudo systemctl start cabin-backend
```

重新部署前端：

```bash
cd /opt/cabin-data-platform/frontend
npm ci
npm run build

sudo rsync -a --delete dist/ /var/www/cabin-platform/
sudo chown -R www-data:www-data /var/www/cabin-platform
sudo systemctl reload nginx
```

## 12. 常见问题

### 12.1 Compose 提示必填环境变量缺失

`deploy/docker-compose.yml` 中对数据库密码、JWT 密钥和初始管理员密码都有强校验：

```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env or environment}
JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set in .env or environment}
BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD:?BOOTSTRAP_ADMIN_PASSWORD must be set in .env or environment}
```

因此必须创建项目根目录 `.env`，并使用以下命令启动：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

### 12.2 后端连接了错误数据库

后端默认 profile 是 `dev`。部署时必须设置：

```env
SPRING_PROFILES_ACTIVE=prod
```

否则后端会使用开发环境配置。

### 12.3 前端请求接口跨域或 404

生产环境前端建议保持：

```env
VITE_API_BASE_URL=/api/v1
```

Compose 模式由前端容器内的 Nginx 将 `/api/v1` 转发到后端容器的 `backend:8080`。传统模式才使用宿主机 Nginx 转发到 `127.0.0.1:8080`。

### 12.4 首次启动后端较慢

首次启动后端时，Flyway 会自动执行数据库迁移脚本，创建业务表和初始化数据。只要日志没有报错，稍等一会儿即可。

### 12.5 GeoJSON 地图返回 404

Compose 部署先检查源码、镜像内构建产物和 HTTP 响应：

```bash
ls -lh frontend/public/map/geojson/manifest.json
docker compose --env-file .env -f deploy/docker-compose.yml exec frontend \
  ls -lh /usr/share/nginx/html/map/geojson/manifest.json
curl -I http://127.0.0.1:18080/map/geojson/manifest.json
```

传统 Nginx 部署按照第 8 节检查 `/var/www/cabin-platform/map/geojson` 和 Nginx 静态文件配置。

### 12.6 页面无法访问时的排查顺序

Compose 部署：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml ps
docker compose --env-file .env -f deploy/docker-compose.yml logs --tail=200 postgres backend frontend
curl http://127.0.0.1:18081/actuator/health
curl -I http://127.0.0.1:18080/
```

传统部署：

```bash
sudo systemctl status nginx
sudo systemctl status cabin-backend
curl http://127.0.0.1:8080/actuator/health
docker ps
sudo nginx -t
```

## 13. 推荐上线检查清单

- `.env` 中数据库用户为 `postgres`，数据库密码和数据源密码均为 `123456`（公网正式环境应换成同一个强密码）。
- `JWT_SECRET` 已替换为至少 32 位随机字符串。
- `BOOTSTRAP_ADMIN_PASSWORD` 已替换为正式管理员初始密码。
- `frontend/public/map/geojson/manifest.json` 已存在。
- Compose 模式下 `postgres`、`backend`、`frontend` 均已启动。
- `curl http://127.0.0.1:18081/actuator/health` 返回正常。
- `curl -I http://127.0.0.1:18080/map/geojson/manifest.json` 返回 `200`。
- 传统模式下 `/etc/cabin-backend.env` 已设置 `SPRING_PROFILES_ACTIVE=prod`，且 Nginx `sudo nginx -t` 校验通过。
- 浏览器可以访问 `http://服务器IP:18080/`。
