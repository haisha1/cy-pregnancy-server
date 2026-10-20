# 腾讯云轻量服务器Docker部署

本地已完成Java 8构建与运行验证后再部署。Dockerfile/Compose为交付模板；本机未安装Docker，不能把模板交付记为镜像构建或云端部署已验证。

## 部署步骤

1. 将cy-pregnancy-server目录复制到服务器，不复制target、.local、.env和本机日志。
2. 安装Docker及Compose插件，在服务器复制`.env.example`为`.env`。
3. 配置独立数据库/Redis密码、至少32字节随机JWT_SECRET、真实WX_APP_ID/WX_APP_SECRET。数据库应用用户为pregnancy，root密码独立。填写ADMIN_READ_KEY和CONTENT_READ_KEY，两份独立密钥分别与管理服务的同名配置保持一致，不复用JWT密钥。
4. 两个服务首次部署前，执行`docker network create cy-pregnancy-internal`创建共享网络；若`docker network inspect cy-pregnancy-internal`已能查到该网络，则跳过创建。两端SERVICE_NETWORK保持相同。执行`docker compose config --quiet`检查配置，再执行`docker compose up -d --build`；管理服务也需在其项目目录分别启动，知识内容读取依赖管理服务可用。
5. 用`docker compose logs --tail=100 api`检查启动和Flyway；不要开启请求体或SDK debug日志。
6. 在服务器访问`curl -f http://127.0.0.1:18731/actuator/health`，确认UP。配置宿主机Nginx HTTPS反向代理，再设置微信小程序request合法域名。

Compose中MySQL/Redis不发布宿主机端口；应用只绑定127.0.0.1:18731。容器里的localhost是容器自己，应用通过mysql、redis服务名连接，不能沿用本机localhost地址。若云服务器已有MySQL/Redis容器，则接入现有私有网络并调整DB_URL/REDIS_HOST，不启动重复实例。

## 发布前需落实

- 当前Java 8/Boot 2.7为兼容本机环境的决定，正式上线前核验全部依赖安全维护情况，制定受支持版本升级或维护方案。
- 配置HTTPS、证书续期、备份、磁盘容量和数据库恢复演练；MYSQL数据在Docker volume中，禁止`docker compose down -v`误删数据。
- 配置微信实际AppID、Secret及平台需要的网络白名单；本工程尚未做真实微信登录端到端验证。
- 当前限流直接取连接IP，宿主Nginx转发后可能把所有请求计入同一窗口。对外开放前应在Nginx按来源IP限流，并补Java对可信代理来源的解析/测试，不直接相信任意X-Forwarded-For。
- Swagger/OpenAPI在prod关闭；正式prod必须显式指定环境，不能以默认local部署。禁止设置日志级别DEBUG采集微信请求细节。
- 会话30天后数据库记录尚未实现定时清理；需上线前增加过期会话及refresh历史清理任务，保留最小必要安全元数据。
- 当前只有账号模块，不应打开前端云同步或上传健康报告。公开服务必须与已实现能力和正式政策一致。

## 升级和回退

发版前备份数据库，核对新迁移。只追加Flyway版本，不修改已执行SQL。回退应用前确认旧应用与新schema兼容；数据库DDL不保证能事务回滚，不能把镜像回退当作数据库回滚。JWT密钥变更会使旧access失效，应配合撤销会话或明确重新登录策略。
