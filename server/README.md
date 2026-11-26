# 小松果后端服务（Spring Boot 3 + MySQL 8）

本目录为「小松果」App 配套的纯接口 Spring Boot 后端工程，直接对接本地 MySQL 8.4 的 `pkmapp` 数据库。

## 启动方式

```powershell
cd d:\pkmAPP\server
mvn spring-boot:run
```

默认监听 `http://127.0.0.1:8080`，健康检查地址：`GET http://127.0.0.1:8080/api/v1/health`。

## 手机模拟器联调

1. 启动本服务（默认 `8080` 端口）。
2. 在 Android 模拟器中打开「小松果」→ 左上角头像 → **数据备份与云同步** → 填写服务器地址：`http://10.0.2.2:8080`。
3. 注册或登录账号后，点击「立即同步」，数据将实时写入本机 MySQL `pkmapp` 数据库。

## 数据库配置（支持环境变量覆盖）

- `PKM_DB_URL`（默认 `jdbc:mysql://127.0.0.1:3306/pkmapp?...`）
- `PKM_DB_USER`（默认 `root`）
- `PKM_DB_PASSWORD`（默认 `123456`）
- `PKM_JWT_SECRET`（上线前请替换为强随机密钥）
- `PKM_ADMIN_TOKEN`（管理员接口 `X-Admin-Token` 请求头密钥）
