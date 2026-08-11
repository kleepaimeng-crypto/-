# 前中后舱网联数据显示平台 Windows 远程服务器 Jar 部署指南

本文档说明如何在远程 Windows 服务器上部署本项目。

推荐部署方式：

- PostgreSQL 安装在 Windows 服务器本机，或使用已有远程 PostgreSQL。
- 后端 Spring Boot 打包为 jar，通过 `java -jar` 启动。
- 前端 Vue/Vite 构建为静态文件，通过 Windows 版 Nginx 托管。
- Nginx 将 `/api/v1` 反向代理到后端 `8080` 端口。

## 1. 服务器准备

建议服务器系统：

- Windows Server 2019 / 2022
- 或 Windows 10 / Windows 11 专业版

需要安装：

- JDK 21
- Node.js 22
- PostgreSQL
- Nginx for Windows
- Git，非必须，也可以手动上传项目代码

## 2. 安装 JDK 21

下载并安装 JDK 21：

```text
https://adoptium.net/temurin/releases/?version=21
```

安装后打开 PowerShell 验证：

```powershell
java -version
```

如果提示找不到 `java`，需要配置环境变量：

```text
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.x.x
Path=%JAVA_HOME%\bin
```

## 3. 安装 Node.js 22

下载 Node.js 22 LTS：

```text
https://nodejs.org/
```

安装后验证：

```powershell
node -v
npm -v
```

前端 `package.json` 要求：

```json
"node": ">=22.12.0"
```

因此 Node 版本不能低于 `22.12.0`。

## 4. 安装 PostgreSQL

下载 PostgreSQL Windows 安装包：

```text
https://www.postgresql.org/download/windows/
```

安装时建议：

- 端口：`5432`
- 数据库超级用户：`postgres`
- 密码：自行设置强密码

安装完成后，可以使用 pgAdmin 或 PowerShell 创建项目数据库和用户。

示例：

```powershell
psql -U postgres
```

进入 psql 后执行：

```sql
CREATE DATABASE cabin_data_platform;
CREATE USER cabin_app WITH PASSWORD '替换为强密码';
GRANT ALL PRIVILEGES ON DATABASE cabin_data_platform TO cabin_app;
```

切换到业务库后授权 schema：

```sql
\c cabin_data_platform
GRANT ALL ON SCHEMA public TO cabin_app;
ALTER SCHEMA public OWNER TO cabin_app;
```

后端首次启动时会通过 Flyway 自动执行数据库迁移脚本。

## 5. 上传或拉取项目代码

推荐部署目录：

```text
D:\deploy\cabin-data-platform
```

目录结构建议保持：

```text
D:\deploy\cabin-data-platform
├── backend
├── frontend
├── deploy
├── simulator
└── .env.example
```

如果服务器可以访问 Git 仓库：

```powershell
cd D:\deploy
git clone <你的仓库地址> cabin-data-platform
```

如果不能访问 Git，可以在本机打包项目后上传到服务器。

## 6. 构建后端 jar

进入后端目录：

```powershell
cd D:\deploy\cabin-data-platform\backend
```

执行构建：

```powershell
.\mvnw.cmd clean package -DskipTests
或者
mvn clean package -DskipTests
```

构建成功后，jar 文件位于：

```text
D:\deploy\cabin-data-platform\backend\target\*.jar
```

创建后端运行目录：

```powershell
mkdir D:\deploy\cabin-backend
copy D:\deploy\cabin-data-platform\backend\target\*.jar D:\deploy\cabin-backend\app.jar
```

## 7. 配置后端启动环境变量

在 `D:\deploy\cabin-backend` 下新建 `start-backend.ps1`：

```powershell
cd D:\deploy\cabin-backend
notepad start-backend.ps1
```

写入：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5432/cabin_data_platform"
$env:SPRING_DATASOURCE_USERNAME = "cabin_app"
$env:SPRING_DATASOURCE_PASSWORD = "替换为数据库密码"

$env:JWT_ISSUER = "cabin-data-platform"
$env:JWT_SECRET = "替换为至少32位随机字符串"
$env:JWT_EXPIRES_IN_SECONDS = "7200"

$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "替换为管理员初始密码"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@example.com"

$env:UDP_ENABLED = "true"
$env:APP_ZONE_ID = "Asia/Shanghai"

java -jar D:\deploy\cabin-backend\app.jar
```

生成 `JWT_SECRET` 可以在 PowerShell 中执行：

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

注意：项目后端默认 profile 是 `dev`，正式部署必须设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
```

否则后端会使用开发环境配置。

## 8. 手动启动后端 jar

使用管理员 PowerShell 执行：

```powershell
cd D:\deploy\cabin-backend
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1
```

看到 Spring Boot 启动日志后，打开新 PowerShell 验证：

```powershell
curl http://127.0.0.1:8080/actuator/health
```

如果返回健康状态，说明后端启动成功。

## 9. 注册为 Windows 服务，可选但推荐

手动窗口启动适合临时调试。正式部署建议使用 NSSM 将 jar 注册为 Windows 服务。

下载 NSSM：

```text
https://nssm.cc/download
```

假设解压到：

```text
D:\tools\nssm
```

创建一个真正用于服务运行的 bat 文件：

```powershell
notepad D:\deploy\cabin-backend\start-backend.bat
```

写入：

```bat
@echo off
set SPRING_PROFILES_ACTIVE=prod

set SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/cabin_data_platform
set SPRING_DATASOURCE_USERNAME=cabin_app
set SPRING_DATASOURCE_PASSWORD=替换为数据库密码

set JWT_ISSUER=cabin-data-platform
set JWT_SECRET=替换为至少32位随机字符串
set JWT_EXPIRES_IN_SECONDS=7200

set BOOTSTRAP_ADMIN_USERNAME=admin
set BOOTSTRAP_ADMIN_PASSWORD=替换为管理员初始密码
set BOOTSTRAP_ADMIN_EMAIL=admin@example.com

set UDP_ENABLED=true
set APP_ZONE_ID=Asia/Shanghai

java -jar D:\deploy\cabin-backend\app.jar
```

使用 NSSM 注册服务：

```powershell
D:\tools\nssm\win64\nssm.exe install CabinBackend
```

在弹出的窗口中配置：

- Application path：`D:\deploy\cabin-backend\start-backend.bat`
- Startup directory：`D:\deploy\cabin-backend`
- Service name：`CabinBackend`

也可以用命令行直接配置：

```powershell
D:\tools\nssm\win64\nssm.exe install CabinBackend D:\deploy\cabin-backend\start-backend.bat
D:\tools\nssm\win64\nssm.exe set CabinBackend AppDirectory D:\deploy\cabin-backend
D:\tools\nssm\win64\nssm.exe set CabinBackend Start SERVICE_AUTO_START
```

启动服务：

```powershell
net start CabinBackend
```

停止服务：

```powershell
net stop CabinBackend
```

查看服务状态：

```powershell
sc query CabinBackend
```

## 10. 构建前端

进入前端目录：

```powershell
cd D:\deploy\cabin-data-platform\frontend
```

创建生产环境配置：

```powershell
copy .env.example .env.production
notepad .env.production
```

建议内容：

```env
VITE_API_BASE_URL=/api/v1
VITE_MAP_MIN_ZOOM=3
VITE_MAP_MAX_ZOOM=10
```

安装依赖并构建：

```powershell
npm ci
npm run build
```

构建结果目录：

```text
D:\deploy\cabin-data-platform\frontend\dist
```

## 11. 安装并配置 Windows 版 Nginx

下载 Nginx for Windows：

```text
https://nginx.org/en/download.html
```

假设解压到：

```text
D:\tools\nginx
```

创建前端部署目录：

```powershell
mkdir D:\deploy\cabin-web
```

复制前端构建产物：

```powershell
Remove-Item D:\deploy\cabin-web\* -Recurse -Force
Copy-Item D:\deploy\cabin-data-platform\frontend\dist\* D:\deploy\cabin-web\ -Recurse
```

编辑 Nginx 配置：

```powershell
notepad D:\tools\nginx\conf\nginx.conf
```

将 `server` 配置调整为：

```nginx
server {
    listen       80;
    server_name  localhost;

    root   D:/deploy/cabin-web;
    index  index.html;

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
}
```

注意：Nginx 配置里的 Windows 路径建议使用 `/`，例如：

```nginx
root D:/deploy/cabin-web;
```

不要写成：

```nginx
root D:\deploy\cabin-web;
```

## 12. 启动 Nginx

进入 Nginx 目录：

```powershell
cd D:\tools\nginx
```

检查配置：

```powershell
.\nginx.exe -t
```

启动：

```powershell
.\nginx.exe
```

重载配置：

```powershell
.\nginx.exe -s reload
```

停止：

```powershell
.\nginx.exe -s stop
```

浏览器访问：

```text
http://服务器IP/
```

## 13. Windows 防火墙放行

需要放行 HTTP 端口：

```powershell
New-NetFirewallRule -DisplayName "Cabin Web HTTP 80" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow
```

如果需要外部直接访问后端，放行 `8080`，但生产环境通常不建议暴露后端端口：

```powershell
New-NetFirewallRule -DisplayName "Cabin Backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

如果模拟器或外部设备需要向服务器发送 UDP 数据，需要放行 `8090` 到 `8096`：

```powershell
New-NetFirewallRule -DisplayName "Cabin UDP Ingest 8090-8096" -Direction Inbound -Protocol UDP -LocalPort 8090-8096 -Action Allow
```

如果模拟器和后端在同一台 Windows 服务器，且发送到 `127.0.0.1`，一般不需要放行 UDP 端口。

## 14. 可选：运行数据模拟器

进入模拟器目录：

```powershell
cd D:\deploy\cabin-data-platform\simulator
```

发送一轮测试数据：

```powershell
python run_simulator.py --once --summary
```

持续发送：

```powershell
python run_simulator.py --config simulator_config.example.json --summary
```

如果模拟器和后端部署在同一台服务器，`udpHost` 使用：

```json
{
  "udpHost": "127.0.0.1"
}
```

如果模拟器部署在其他机器，需要将 `udpHost` 改为 Windows 服务器 IP，并确保 Windows 防火墙已放行 UDP 端口。

## 15. 常用维护命令

### 15.1 手动重启后端 jar

如果是 PowerShell 窗口手动启动，直接关闭窗口或 `Ctrl + C` 后重新执行：

```powershell
cd D:\deploy\cabin-backend
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1
```

### 15.2 Windows 服务方式重启后端

```powershell
net stop CabinBackend
net start CabinBackend
```

### 15.3 重新部署后端

```powershell
cd D:\deploy\cabin-data-platform
git pull

cd backend
.\mvnw.cmd clean package -DskipTests

net stop CabinBackend
copy /Y target\*.jar D:\deploy\cabin-backend\app.jar
net start CabinBackend
```

如果没有注册 Windows 服务，则关闭原来的后端窗口后，重新运行：

```powershell
cd D:\deploy\cabin-backend
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1
```

### 15.4 重新部署前端

```powershell
cd D:\deploy\cabin-data-platform
git pull

cd frontend
npm ci
npm run build

Remove-Item D:\deploy\cabin-web\* -Recurse -Force
Copy-Item D:\deploy\cabin-data-platform\frontend\dist\* D:\deploy\cabin-web\ -Recurse

cd D:\tools\nginx
.\nginx.exe -s reload
```

### 15.5 检查端口占用

查看 `8080`：

```powershell
netstat -ano | findstr :8080
```

查看 `80`：

```powershell
netstat -ano | findstr :80
```

根据 PID 查进程：

```powershell
tasklist | findstr <PID>
```

## 16. 常见问题

### 16.1 后端启动后连接数据库失败

检查以下配置：

```powershell
$env:SPRING_DATASOURCE_URL
$env:SPRING_DATASOURCE_USERNAME
$env:SPRING_DATASOURCE_PASSWORD
```

确认 PostgreSQL 服务已启动：

```powershell
Get-Service | findstr postgresql
```

也可以在服务管理器中检查 PostgreSQL 服务状态。

### 16.2 后端用了 dev 配置

必须设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
```

否则项目会使用 `application-dev.yml` 中的开发配置。

### 16.3 前端页面可以打开，但接口 404 或请求失败

检查前端生产配置：

```env
VITE_API_BASE_URL=/api/v1
```

检查 Nginx 是否配置了：

```nginx
location /api/v1/ {
    proxy_pass http://127.0.0.1:8080/api/v1/;
}
```

然后验证后端：

```powershell
curl http://127.0.0.1:8080/actuator/health
```

### 16.4 Nginx 启动失败

先检查配置：

```powershell
cd D:\tools\nginx
.\nginx.exe -t
```

常见原因：

- `80` 端口被 IIS 或其他程序占用。
- `root` 路径写错。
- Windows 路径用了反斜杠。

### 16.5 首次启动后端较慢

首次启动后端时，Flyway 会执行数据库迁移脚本，创建业务表和初始化数据。只要日志没有报错，等待完成即可。

## 17. 上线检查清单

- JDK 21 已安装，`java -version` 正常。
- Node.js 22 已安装，`node -v` 正常。
- PostgreSQL 已启动。
- 已创建 `cabin_data_platform` 数据库。
- 已创建数据库用户 `cabin_app`。
- 后端 jar 已构建并复制到 `D:\deploy\cabin-backend\app.jar`。
- 后端启动脚本中已设置 `SPRING_PROFILES_ACTIVE=prod`。
- 后端启动脚本中数据库密码正确。
- `JWT_SECRET` 已替换为至少 32 位随机字符串。
- `BOOTSTRAP_ADMIN_PASSWORD` 已替换为正式管理员初始密码。
- `curl http://127.0.0.1:8080/actuator/health` 正常。
- 前端 `npm run build` 成功。
- Nginx `.\nginx.exe -t` 校验通过。
- Windows 防火墙已放行 `80` 端口。
- 浏览器可以访问 `http://服务器IP/`。
