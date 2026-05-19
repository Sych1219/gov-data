# API 设计问题记录

> 本文档记录当前 Controller 层和 DTO 层存在的设计问题，供重构参考。

---

## 一、Controller 层问题

### 1.1 TrafficImageController 缺少 API 版本号

所有 Controller 都使用 `/api/v1/` 前缀，唯独 `TrafficImageController` 是 `/api/cameras`。

```
/api/v1/taxis       ✓
/api/v1/zones       ✓
/api/v1/tiles       ✓
/api/v1/agent-memory  ✓
/api/cameras        ✗  ← 缺少 /v1/
```

未来若需要发布 v2 版本，这个 Controller 无法按相同规则进行版本迭代。

---

### 1.2 ZoneController 直接依赖 Repository，绕过 Service 层

`ZoneController` 直接注入了 `ZoneRepository`，其他所有 Controller 都通过 Service 间接访问数据库，只有这一个例外。这破坏了分层架构的一致性，也让业务逻辑没有固定归属。

```
其他 Controller → Service → Repository  ✓
ZoneController  → Repository            ✗
```

---

### 1.3 TileController 只有一个接口，职责归属不明

`TileController` 只有一个端点：`GET /api/v1/tiles/taxis/timeline/{z}/{x}/{y}.pbf`。

这个接口本质上是 taxi 历史位置数据的 MVT 二进制格式，和 `TaxiController` 的 `/history/snapshots` 使用同一批数据。独立出一个 Controller 增加了维护成本，将其并入 `TaxiController` 更合适。

---

### 1.4 TrafficImageController 单个方法包揽四种查询，且缺少参数互斥校验

`GET /api/cameras` 用 if-else 链在一个方法里处理四种完全不同的查询逻辑：

```
有 expressway  → 按高速路筛选
有 search      → 按关键词搜索
有 lat + lng   → 按经纬度范围搜索
都没有         → 返回全量
```

**问题**：如果只传了 `lat` 没传 `lng`，代码不会报错，而是静默降级为"返回全量"。这种行为对调用方不透明，容易造成意外结果。

---

### 1.5 TileController 的 snapshots 参数缺少输入校验

`snapshots` 参数通过逗号分隔字符串传递多个 ID（`?snapshots=7184,7194,7204`），解析时直接调用 `Long.parseLong()`，没有任何错误处理。若客户端传入非数字内容，会抛出未捕获的 `NumberFormatException`，返回 500。

---

## 二、Request Schema 问题

### 2.1 将 JSON 序列化成字符串再塞入请求体（反模式）

`SaveTrajectoryRequest.eventsJson` 和 `CreateHintRequest.embeddingJson` 都是 `String` 类型，但存储的实际上是一段 JSON。

这意味着客户端需要先把对象序列化成字符串，再嵌入 JSON body；服务端收到后还要再反序列化一次。整个链路多了一层不必要的编解码。

```json
// 现在客户端需要发送这样的请求
{
  "eventsJson": "{\"step\":1,\"action\":\"query\"}"
}

// 应该直接是
{
  "events": { "step": 1, "action": "query" }
}
```

受影响的字段：
- `SaveTrajectoryRequest.eventsJson`
- `CreateHintRequest.embeddingJson`

---

### 2.2 TaxiPolygonRequest 和 TaxiRouteRequest 对 GeoJSON 的处理方式不一致

两个功能类似的请求体对 GeoJSON 字段的处理方式截然不同：

| 请求体 | 字段 | 类型 | 是否有校验 |
|---|---|---|---|
| `TaxiPolygonRequest` | `polygon` | `GeoJsonPolygon` | 有（`@Valid`）|
| `TaxiRouteRequest` | `route` | `JsonNode` | 无 |

`route` 接受任意 JSON 都不会在 Controller 层报错，错误只会在业务层更深处抛出，且错误信息不友好。

---

### 2.3 StoreAnalysisRequest 的分析字段缺少枚举约束

`StoreAnalysisRequest` 中的 `congestion`、`vehicleDensity`、`weather` 等字段都是 `@NotBlank String`，API 层无法约束值域，任意字符串都能通过校验。

这些字段应有明确的取值范围（例如 `low / medium / high`），否则存入数据库的值格式无法保证一致。

---

### 2.4 SaveTrajectoryRequest.id 字段命名不明确

字段名是 `id`，但从 Controller 日志的注释（`requestId`）来看，它实际上代表的是上游请求的 ID，而非 trajectory 本身的主键。`id` 这个名称歧义较大。

---

## 三、Response Schema 问题

### 3.1 ZoneGeometryData 错误归属于 TaxiResponseData

`TaxiResponseData` 是一个 sealed interface，但其子类型中包含了 `ZoneGeometryData`：

```java
public sealed interface TaxiResponseData
    permits SpatialQueryData, TimelineData, ZoneGeometryData {}
//                                          ^^^^^^^^^^^^^^^^
//                                          这和 Taxi 有什么关系？
```

`ZoneGeometryData` 是 Zone 的地理边界数据，和 Taxi 没有任何关联。实际上 `ZoneController.getGeometry()` 返回的是 `ApiResponse<ZoneGeometryData>`，完全不经过 `TaxiResponseData`，所以这个 `implements` 关系没有任何实际用途，只会让人困惑。

---

### 3.2 CameraQueryResponse 的 meta 字段设计为 fat object

`CameraMeta` 把四种查询模式的上下文字段全部堆在一起：

```java
public class CameraMeta {
    String expressway;  // 仅按高速路查询时有值
    String query;       // 仅按关键词查询时有值
    Double lat;         // 仅按经纬度查询时有值
    Double lng;
    Integer radius;
    int count;          // 所有情况都有
}
```

根据查询类型不同，响应中大多数字段是 `null`，客户端必须自行判断哪些字段有意义。

`TaxiController` 用 `QueryContext` sealed interface 的多态子类型解决了同样的问题，但 `CameraQueryResponse` 没有对齐这个做法。

---

### 3.3 时间字段的类型在不同 DTO 中不统一

| DTO | 字段 | 类型 |
|---|---|---|
| `CameraDetail` | `timestamp` | `String` |
| `CameraAnalysisDetail` | `analyzedAt` | `String` |
| `StoreAnalysisResponse` | `analyzedAt` | `String` |
| `AgentHintResponse` | `createdAt` | `OffsetDateTime` |
| `AgentObservationResponse` | `createdAt` | `OffsetDateTime` |
| `SpatialQueryData` | `snapshotTime` | `OffsetDateTime` |

Camera 相关的时间用 `String`，其余用 `OffsetDateTime`。用 `String` 的问题在于序列化格式没有约束，不同地方可能产生格式不一致的时间字符串，客户端解析需要特殊处理。

---

### 3.4 SpatialQueryData 被 count-only 和带位置两类接口共用，语义不清

`SpatialQueryData` 有一个 `locations` 字段（GeoJSON 坐标集合），但它被以下两类接口共用：

| 接口 | 实际需要 locations？ |
|---|---|
| `GET /nearby` | 需要，返回具体坐标 |
| `GET /zone/count` | 不需要，只要数量 |
| `GET /road/count` | 不需要，只要数量 |
| `POST /polygon/count` | 不需要，只要数量 |

count-only 接口的 `locations` 字段永远是 `null`，但客户端从类型上无法预知这一点。

---

### 3.5 写操作返回 ApiResponse&lt;Void&gt;，客户端无法引用创建的资源

`POST /trajectories` 和 `POST /hints/{id}/observations` 都返回 `ApiResponse<Void>`（`data` 为 null）。

客户端创建完资源后，无法得知资源的 ID，也就无法在后续操作中引用它。至少应该返回新建资源的 ID。

---

### 3.6 AgentHintResponse 暴露 embeddingJson 字符串

`AgentHintResponse` 包含 `embeddingJson: String`，这是把 embedding 向量的 JSON 表示直接暴露给客户端。和 2.1 中的问题对称——既然请求时用字符串存，响应时也只能用字符串返回，客户端需要再次解析。

---

## 问题优先级汇总

| 优先级 | 问题 | 影响范围 |
|---|---|---|
| 高 | 3.1 ZoneGeometryData 错误实现 TaxiResponseData | 代码逻辑混乱 |
| 高 | 2.1 / 3.6 JSON 字符串嵌套 | Agent Memory 全部接口 |
| 高 | 1.1 TrafficImageController 缺少版本号 | 版本管理 |
| 中 | 1.4 / 1.5 Camera 查询无参数互斥校验、Tile 参数无校验 | 运行时 500 |
| 中 | 3.3 时间类型不统一 | Camera 全部接口 |
| 中 | 2.2 TaxiRouteRequest 缺少 GeoJSON 校验 | /route/count |
| 中 | 3.2 CameraMeta fat object | Camera 查询响应 |
| 低 | 1.2 ZoneController 绕过 Service 层 | 架构一致性 |
| 低 | 1.3 TileController 职责归属 | 代码组织 |
| 低 | 2.3 StoreAnalysisRequest 缺枚举约束 | Camera 分析接口 |
| 低 | 2.4 SaveTrajectoryRequest.id 命名 | 可读性 |
| 低 | 3.4 SpatialQueryData 复用语义不清 | Taxi 查询响应 |
| 低 | 3.5 写操作返回 Void | AgentMemory 写接口 |
