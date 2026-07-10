# 深色地图瓦片下载指南

## 目标

替换 `frontend/map/tiles_street/` 中的白色地图瓦片为深色主题瓦片。

---

## 推荐瓦片源

### 🌙 方案A：CartoDB Dark Matter（推荐）
- **风格**：简洁深色，专为数据可视化设计
- **URL模板**：`https://{a-d}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png`
- **授权**：免费，需标注来源
- **优点**：干净、对比度高、适合飞行轨迹展示

### 🗺️ 方案B：Stadia Maps Alidade Dark
- **风格**：现代深色，细节丰富
- **URL模板**：`https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}.png`
- **授权**：免费额度 200k 次/月
- **优点**：视觉精致，中文支持好

### 🌍 方案C：MapBox Dark
- **风格**：专业深色
- **需要**：注册 MapBox 账号获取 Access Token
- **URL模板**：`https://api.mapbox.com/styles/v1/mapbox/dark-v10/tiles/{z}/{x}/{y}?access_token=YOUR_TOKEN`
- **优点**：可定制，质量最高

---

## 下载工具推荐

### 🔧 工具1：Mobile Atlas Creator (MOBAC) ⭐推荐

**下载地址**：https://mobac.sourceforge.io/

**特点**：
- Windows/Mac/Linux 全平台
- 图形界面，操作简单
- 支持多种瓦片源

**使用步骤**：

1. **下载并解压 MOBAC**
   ```
   下载：MOBAC-2.x.x.zip
   解压后运行：Mobile Atlas Creator.exe (Windows) 或 start.sh (Mac/Linux)
   ```

2. **添加自定义瓦片源**
   
   在 `mapsources` 文件夹创建 `cartodb-dark.xml`：
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <customMapSource>
       <name>CartoDB Dark Matter</name>
       <minZoom>0</minZoom>
       <maxZoom>20</maxZoom>
       <tileType>png</tileType>
       <url>https://a.basemaps.cartocdn.com/dark_all/{$z}/{$x}/{$y}.png</url>
   </customMapSource>
   ```

3. **配置下载区域**
   - 启动 MOBAC
   - 选择地图源：`CartoDB Dark Matter`
   - 在地图上框选中国区域（或你需要的范围）
   - 建议范围：
     - 纬度：18°N - 54°N
     - 经度：73°E - 135°E

4. **设置缩放级别**
   - Zoom levels: `3-10`（与你的配置一致）

5. **选择输出格式**
   - Atlas Format: `OSMDroid ZIP`
   - 或直接选 `TileStore` 格式

6. **开始下载**
   - 点击 `Create Atlas`
   - 等待下载完成（可能需要1-2小时，取决于网速和范围）

7. **放置文件**
   ```
   下载后解压，将结构调整为：
   frontend/map/tiles_street_dark/
       3/
           4/
               2.png
               3.png
               ...
       4/
       ...
       10/
   ```

---

### 🔧 工具2：TileMill Desktop（高级用户）

**下载地址**：https://tilemill-project.github.io/tilemill/

**特点**：
- 可自定义样式
- 使用 CartoCSS 编写样式
- 可导出 MBTiles 格式

**适合**：需要完全自定义地图样式的用户

---

### 🔧 工具3：Python脚本批量下载（程序员推荐）

**创建下载脚本** `download_tiles.py`：

```python
import os
import requests
from pathlib import Path

# 配置
OUTPUT_DIR = "frontend/map/tiles_street_dark"
TILE_URL = "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
MIN_ZOOM = 3
MAX_ZOOM = 10

# 中国区域范围（粗略）
LAT_RANGE = (18, 54)  # 纬度
LON_RANGE = (73, 135)  # 经度

def latlon_to_tile(lat, lon, zoom):
    """经纬度转瓦片坐标"""
    import math
    lat_rad = math.radians(lat)
    n = 2.0 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y

def download_tiles():
    Path(OUTPUT_DIR).mkdir(parents=True, exist_ok=True)
    
    for z in range(MIN_ZOOM, MAX_ZOOM + 1):
        x_min, y_min = latlon_to_tile(LAT_RANGE[1], LON_RANGE[0], z)
        x_max, y_max = latlon_to_tile(LAT_RANGE[0], LON_RANGE[1], z)
        
        print(f"Downloading zoom level {z}...")
        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                url = TILE_URL.format(z=z, x=x, y=y)
                output_path = Path(OUTPUT_DIR) / str(z) / str(x)
                output_path.mkdir(parents=True, exist_ok=True)
                file_path = output_path / f"{y}.png"
                
                if file_path.exists():
                    continue
                
                try:
                    response = requests.get(url, timeout=10)
                    if response.status_code == 200:
                        with open(file_path, 'wb') as f:
                            f.write(response.content)
                        print(f"✓ {z}/{x}/{y}")
                    else:
                        print(f"✗ {z}/{x}/{y} - HTTP {response.status_code}")
                except Exception as e:
                    print(f"✗ {z}/{x}/{y} - {e}")

if __name__ == "__main__":
    download_tiles()
```

**运行**：
```bash
pip install requests
python download_tiles.py
```

---

## 切换到深色瓦片

### 选项1：替换现有瓦片（推荐）

下载完成后，替换原有文件：
```bash
# 备份白色瓦片
mv frontend/map/tiles_street frontend/map/tiles_street_backup

# 重命名深色瓦片
mv frontend/map/tiles_street_dark frontend/map/tiles_street
```

### 选项2：修改配置使用新目录

修改 `frontend/vite.config.ts` (第10行)：
```typescript
const offlineMapRoot = resolve(configDir, 'map/tiles_street_dark')
```

---

## 注意事项

### ⚠️ 文件大小
- 缩放级别 3-10，中国区域约 **2-5 GB**
- 下载前确保磁盘空间充足

### ⚠️ 下载时间
- 取决于网速和服务器限制
- 建议使用多线程下载工具
- 可能需要 1-3 小时

### ⚠️ 使用授权
- CartoDB/OpenStreetMap：需标注 `© OpenStreetMap contributors © CartoDB`
- MapBox：需注册账号，遵守用量限制
- Stadia：免费额度有限，商用需付费

### ⚠️ 测试建议
先下载小范围（如只下载缩放级别 5-7）测试效果，满意后再下载完整数据。

---

## 验证下载

下载完成后检查结构：
```bash
# 检查文件数量
find frontend/map/tiles_street -name "*.png" | wc -l

# 查看某个瓦片
# Windows
start frontend/map/tiles_street/6/50/30.png

# Mac
open frontend/map/tiles_street/6/50/30.png
```

瓦片应该显示为深色背景的地图。

---

## 快速测试（可选）

如果想先测试效果，可以临时使用在线深色瓦片：

修改 `frontend/.env.local`：
```env
VITE_OFFLINE_MAP_TILE_URL=https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png
```

重启开发服务器，看看深色地图效果。满意后再下载离线版本。

---

## 故障排查

### 问题1：下载的瓦片仍然是白色
- 检查 URL 模板是否正确
- 尝试在浏览器中打开示例 URL 验证

### 问题2：地图显示不完整
- 检查缩放级别范围是否匹配
- 验证经纬度范围是否覆盖你的飞行区域

### 问题3：下载速度慢
- 使用多线程下载工具
- 考虑使用国内镜像源（如高德、天地图）

---

## 推荐流程

1. **先测试在线版本**（5分钟）
   - 修改 `.env.local` 使用在线深色瓦片
   - 确认视觉效果满意

2. **下载小范围测试**（30分钟）
   - 只下载缩放级别 5-7
   - 验证下载流程和文件结构

3. **下载完整数据**（1-2小时）
   - 下载缩放级别 3-10
   - 替换原有白色瓦片

---

## 需要帮助？

如果下载过程中遇到问题，告诉我具体错误信息，我可以帮你调试。
