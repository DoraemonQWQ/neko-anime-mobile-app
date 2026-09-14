# data/api

后端**接口调用**放这里（只负责"怎么调、怎么解"）：

- `ApiClient.kt`：OkHttp 客户端、统一鉴权（登录后的 JWT）、错误信息、超时（读配置文件）
- `AnimeApi.kt` / `UserApi.kt`：按后端控制器分组的接口方法
- 请求/响应模型：放 `model/dto/`（跟后端一样按 dto / entity 分组），
  转换到界面实体（`model/entity/`）在 `data/repository/` 里做

配套约定：

- 只负责"怎么调、怎么解"，不做业务判断；
- 业务规则放 `service/`，缓存与多来源组合放 `data/repository/`。
- 接口地址、超时、首页板块等可调项全部走配置文件（`assets/config/config.<模式>.json`），
  映射类在 `config/`，代码里不要写死。
