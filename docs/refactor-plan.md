# 代码库优化方案

> 分析日期：2026-05-18  
> 目标：提升代码可读性，消除 API 冗余

---

## 一、API 冗余优化

### 1.1 Camera 端点合并

**现状：** 四个端点本质上是"按不同条件过滤的摄像头列表"，结构高度重复。

| 端点 | 过滤方式 |
|------|---------|
| `GET /api/cameras/` | 无过滤，返回全部 |
| `GET /api/cameras/expressway/{code}` | 按高速公路代码过滤 |
| `GET /api/cameras/search` | 按地名关键词过滤 |
| `GET /api/cameras/nearby` | 按经纬度 + 半径过滤 |

**建议方案：** 合并为一个支持多种过滤参数的端点：

```
GET /api/cameras?expressway=PIE
GET /api/cameras?search=orchard
GET /api/cameras?lat=1.3&lng=103.8&radius=500
GET /api/cameras
```

响应结构统一为：

```json
{
  "cameras": [...],
  "meta": {
    "expressway": "PIE",
    "query": null,
    "lat": null,
    "lng": null,
    "radius": null,
    "count": 12
  }
}
```

---

### 1.2 Camera 响应 Schema 合并

**现状：** 四个几乎相同的响应 Schema，核心都是 `cameras: List<CameraDetail>`，只有 meta 字段不同。

| Schema | 多余字段 |
|--------|---------|
| `CameraListAllResponse` | 无 |
| `CameraListResponse` | `expressway`, `cameraCount` |
| `SearchResponse` | `query` |
| `NearbyResponse` | `lat`, `lng`, `radius` |

**建议方案：** 统一为一个 `CameraQueryResponse`：

```java
public record CameraQueryResponse(
    List<CameraDetail> cameras,
    CameraMeta meta         // nullable，由各查询方式填充
) {}

public record CameraMeta(
    String expressway,
    String query,
    Double lat,
    Double lng,
    Integer radius,
    int count
) {}
```

---

### 1.3 QueryContext 子类简化

**现状：** 7 个 QueryContext 子类（`RadiusContext`、`ZoneContext`、`PolygonContext`、`RoadContext`、`RouteContext`、`NearestContext`），每增加一种查询方式就需要新增一个类。

**建议方案（可选）：** 如果后续查询类型继续增加，可以考虑统一结构。当前 7 个子类在类型安全上有优势，短期内不必强制合并，但需要意识到这是扩展成本所在。

---

## 二、代码可读性优化

### 2.1 Camera 查询改为数据库 JOIN（高优先级）

**现状：** `TrafficImageQueryService` 里的 `listAllCameras()`、`getCamerasByExpressway()` 等方法采用全量加载 + 内存 Join 模式：

```java
// 当前模式（伪代码）
List<Camera> cameras = cameraRepository.findAll();
Map<String, CameraSnapshot> snapshots = snapshotRepo.findLatestPerCamera()
    .stream().collect(toMap(...));
Map<String, CameraAnalysis> analyses = analysisRepo.findByCameraIds(...)
    .stream().collect(toMap(...));

// 在 Java 层手动拼接
cameras.stream().map(c -> new CameraDetail(
    c, snapshots.get(c.getCameraId()), analyses.get(c.getCameraId())
)).toList();
```

**问题：** 当前实现发起 3 次独立查询，并在 Java 层手动拼接结果，逻辑分散且冗余。

**建议方案：** 在 `CameraRepository`（或 `TileRepository`）层用 `LEFT JOIN` 一次性取出所需数据：

```sql
SELECT c.*, cs.image_url, cs.timestamp, ca.congestion, ca.summary
FROM cameras c
LEFT JOIN LATERAL (
    SELECT * FROM camera_snapshots
    WHERE camera_id = c.camera_id
    ORDER BY timestamp DESC LIMIT 1
) cs ON true
LEFT JOIN camera_analyses ca ON ca.camera_id = c.camera_id
WHERE c.expressway = :expressway   -- 按需添加过滤条件
```

---

### 2.2 Camera `/nearby` 改用 PostGIS 查询

**现状：** `getNearbyCameras()` 是把全部摄像头加载进内存，再用 Java 实现 Haversine 公式过滤。

**问题：** 与 Taxi `/nearby`（直接走 PostGIS `ST_DWithin`）逻辑不一致，且性能差。

**建议方案：** 在 `CameraRepository` 增加空间查询方法（类比 `TaxiPositionRepository`）：

```sql
SELECT *, ST_Distance(geog, ST_MakePoint(:lon, :lat)::geography) AS distance_m
FROM cameras
WHERE ST_DWithin(geog, ST_MakePoint(:lon, :lat)::geography, :radiusM)
ORDER BY distance_m
```

前提：Camera 表需要有 `geog` 列（由 `latitude`/`longitude` 生成），可以用 generated column 实现。

---

### 2.3 `lat/lon` 数据类型统一

**现状：**
- `Camera.latitude / longitude` → `BigDecimal`
- `TaxiPosition.latitude / longitude` → `double`
- `getNearbyCameras()` 里有隐式 `.doubleValue()` 精度转换

**建议方案：** 统一为 `double`（GPS 精度 double 完全足够，约 1cm 精度）。修改点：
- `Camera.java` 中 `latitude`、`longitude` 字段类型
- `CameraDetail` DTO 中对应字段
- 删除 `TrafficImageQueryService` 中的 `.doubleValue()` 调用

---

### 2.4 OneMap Token 改为环境变量

**现状：** `application.yml` 中 `zone.seed.onemap.token` 是 JWT Token 明文：

```yaml
zone:
  seed:
    onemap:
      token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
```

**问题：** Token 会随代码提交进入 git 历史，存在泄漏风险。

**建议方案：**

```yaml
zone:
  seed:
    onemap:
      token: ${ONEMAP_TOKEN:}
```

本地开发时通过 `.env` 文件或 IDE 环境变量注入，`.env` 加入 `.gitignore`。

---

### 2.5 TaxiFetchService 补充重试逻辑

**现状：**
- `TrafficImageFetchService` 有指数退避重试（最多 3 次）
- `TaxiFetchService` 无任何重试逻辑

**建议方案：** 两个 Fetch Service 保持一致的错误处理策略，`TaxiFetchService` 补充相同的重试机制。

---

### 2.6 OpenAPI 标题更新

**现状：** `OpenApiConfig.java` 中 Swagger 标题仍为 `"Taxi Availability API"`，但服务已包含摄像头、区域、地图瓦片等模块。

**建议方案：** 改为 `"Singapore Gov Data API"` 或类似更准确的名称。

---

## 三、优先级汇总

| 优先级 | 编号 | 问题 | 影响范围 |
|-------|------|------|---------|
| 🔴 高 | 2.1 | Camera 查询改为数据库 JOIN | 性能 + 可读性 |
| 🔴 高 | 1.1 / 1.2 | Camera 端点 + Schema 合并 | API 清晰度 |
| 🟡 中 | 2.2 | Camera `/nearby` 改用 PostGIS | 性能 + 一致性 |
| 🟡 中 | 2.3 | `lat/lon` 类型统一为 `double` | 可读性 + 一致性 |
| 🟡 中 | 2.4 | OneMap Token 改为环境变量 | 安全性 |
| 🟢 低 | 2.5 | TaxiFetchService 补充重试 | 健壮性 |
| 🟢 低 | 1.3 | QueryContext 子类评估 | 可维护性 |
| 🟢 低 | 2.6 | OpenAPI 标题更新 | 文档准确性 |

---

## 四、涉及的主要文件

| 文件 | 涉及优化 |
|------|---------|
| `controller/TrafficImageController.java` | 1.1 端点合并 |
| `service/TrafficImageQueryService.java` | 1.1、2.1、2.2、2.3 |
| `repository/CameraRepository.java` | 2.1、2.2 |
| `dto/response/CameraListAllResponse.java` | 1.2 合并删除 |
| `dto/response/CameraListResponse.java` | 1.2 合并删除 |
| `dto/response/SearchResponse.java` | 1.2 合并删除 |
| `dto/response/NearbyResponse.java` | 1.2 合并删除 |
| `domain/Camera.java` | 2.3 类型修改 |
| `service/TaxiFetchService.java` | 2.5 补充重试 |
| `config/OpenApiConfig.java` | 2.6 标题更新 |
| `resources/application.yml` | 2.4 Token 环境变量化 |
