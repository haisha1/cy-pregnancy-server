# 产科工具 · 小程序用户端后端

生产部署见 [Jenkins + Docker](../docs/DEPLOYMENT.md)。Jenkins 构建 JAR，Dockerfile 生成运行镜像，Compose 仅更新用户服务，生产配置通过 Jenkins Secret file 注入。

管理 Web 由宝塔代理到 127.0.0.1:18080；用户 API 绑定 127.0.0.1:18731。小程序使用独立 HTTPS API 域名，服务间使用共享 Docker 网络。

本地微信凭据：复制[不含密钥的模板](config/wechat-local.properties.example)到`.local/wechat.properties`，填写WX_APP_ID、WX_APP_SECRET并重启本地Java服务。local配置从本项目目录或工作区根目录自动读取该文件，`prepare-local.ps1`不会覆盖它。文件被Git和Docker忽略，不打进JAR或部署包；服务器 prod 通过 Jenkins Secret file 注入环境变量，两者分别配置。不要把AppSecret填到小程序manifest、VITE变量或管理Web。

2026-10-19增补：已与uni-app接通游客/显式微信登录、串行续期、退出及可选头像昵称资料接口，V3新增昵称、私有头像和资料版本。健康数据仍不上传。配置、接口、19项后端测试及真实微信待验项见[小程序联调记录](../app/cy-pregnancy/docs/微信登录与服务端联调.md)。

当前阶段：**基础工程与账号鉴权模块**。Java 8编译运行，目录独立于前端和未来管理端。代码中的中文注释重点解释业务目的、事务和安全边界。

包结构按技术层组织：`common / entity / dto / mapper / service / controller`，另有`config / security / integration`。主业务使用`Service接口 + service/impl实现`，请求与响应DTO分文件存放。HTTP调用方向为Controller → Service → Mapper，Entity与DTO承担不同的数据职责。完整目录见[代码阅读指南](docs/01-代码阅读指南.md)。

## 1. 已实现与尚未实现

已实现Spring Boot工程、MyBatis-Plus、MySQL/Flyway迁移、Redis登录限流、WxJava真实登录适配、JWT验签、权威会话校验、refresh轮换/重放检测、退出、当前账户、真实能力清单、统一响应/错误和本地OpenAPI。

未实现孕期档案、检查、授权回执、偏好同步、云同步/冲突、Java医学模型复核、参考包发布、导出删除、反馈、OCR、提醒及远程分享。管理端已在独立工程实现账号/权限和知识审核发布，见[管理端说明](../cy-pregnancy-admin/README.md)，尚未接通用户端内容分发。用户端未实现能力保持false，前端代码未修改，不能把后端账号模块完成称为整个小程序服务接通。

## 2. 本机环境

| 项目 | 使用值 |
|---|---|
| Java | `C:/Program Files/Java/jdk-1.8`，实测1.8.0_441 |
| Maven | `D:/code/apache-maven-3.9.16` |
| MySQL | `localhost:3306`，实测8.0.46；local配置为用户指定的root/root |
| 项目数据库 | `cy_pregnancy`；测试专用库`cy_pregnancy_auth_test` |
| Redis | `localhost:6379`，无密码；实测5.0.14.1 |
| 应用端口 | 默认18731，可通过SERVER_PORT覆盖 |

锁定版本：Spring Boot 2.7.18、MyBatis-Plus 3.5.7、WxJava 4.6.0、springdoc 1.8.0；Security、Flyway、MySQL驱动等跟随Boot BOM，完整解析结果由Maven构建确定。

Java 8不兼容Boot 3/4，采用2.7.18是遵照本次环境决定。Boot 2.7.18已结束开源支持，因此“本地可运行”不代表依赖仍获公开安全更新。云端正式发布前需选择JDK/框架升级或有持续安全维护的依赖方案，升级任务见下方技术基线文档，不自动更改用户JDK。

依据：[Boot 2.7.18系统要求](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html)、[开源支持结束公告](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)、[springdoc Boot 2文档](https://springdoc.org/v1/)、[WxJava](https://github.com/binarywang/WxJava)。

## 3. 第一次运行

在PowerShell执行：

```powershell
Set-Location D:/code/cy-pregnancy/cy-pregnancy-server
# 确保本机MySQL/Redis服务已经开启。此脚本只创建项目库，不清空已有表。
powershell -ExecutionPolicy Bypass -File ./scripts/init-local-db.ps1
# 首次运行自动在忽略目录.local生成随机JWT密钥，不会打印密钥。
powershell -ExecutionPolicy Bypass -File ./scripts/dev.ps1 -Task run
```

启动时Flyway执行`src/main/resources/db/migration`，创建账号表。V1执行后不要修改；新增表/字段写V2、V3。若数据库已有不属于本工程的表或历史迁移不一致，先查明原因，不执行clean或强制baseline。

启动后访问：

- [健康检查](http://localhost:18731/actuator/health)：只显示总体状态，包含数据库/Redis可用性。
- [接口文档](http://localhost:18731/swagger-ui/index.html)：仅local开启。
- [OpenAPI JSON](http://localhost:18731/v3/api-docs)：可导入Apifox。
- [能力清单](http://localhost:18731/api/v1/capabilities)：微信配置缺失时auth=false。

IDEA运行：导入本目录pom.xml，Project SDK、Module SDK、Maven Importer/Runner及运行配置JRE都选Java 8（本机注册名称`account-book-jdk8`，路径`C:/Program Files/Java/jdk-1.8`），避免误选OpenJDK 26造成Lombok的`TypeTag :: UNKNOWN`编译错误。

首次执行 `powershell -ExecutionPolicy Bypass -File ./scripts/prepare-local.ps1`，复用或生成`.local/jwt-secret.txt`，导出被git忽略的`.local/application-secrets.properties`；两端内部只读密钥复用管理端本机文件，JWT签名密钥仍分别独立。之后可直接选择共享运行配置`PregnancyServerApplication (local)`启动，无需在IDEA中复制密钥；`dev.ps1`也自动执行相同准备。local配置支持以本项目目录或仓库根目录为工作目录。

若旧运行配置存在空的`JWT_SECRET`，删除该空项；环境变量优先于本机文件。重复准备不会轮换密钥或清理数据库。prod不导入本机密钥文件，部署仍需显式配置正式环境变量。Lombok需要IDE启用注解处理，Maven构建不依赖IDE插件。

## 4. 微信真实登录

本地已配置小程序 AppID，AppSecret 保存在 Git 忽略的 `.local/wechat.properties`。重启 local 服务后读取；真实微信 code 登录仍需联调，配置存在不代表授权已验证。生产配置单独由 Jenkins Secret file 注入。

已准备忽略提交的 `.local/user-prod.env`，用于更新服务器生产配置及 Jenkins 的 `cy-user-prod-env` 凭据。文件含敏感配置，不提交 Git、不打入镜像、不打印到日志。更新 Secret file 后重新运行发布任务，容器才会读取新值。小程序用wx.login取得实时code，调用：

```http
POST /api/v1/auth/wx-login
Content-Type: application/json

{"code":"微信实时code","installationId":"设备安装时随机生成并持久化的标识"}
```

installationId应为16–64位ASCII字母、数字、下划线或连字符；推荐UUID。返回本系统accessToken和refreshToken。业务请求携带`Authorization: Bearer <accessToken>`，不能用微信openid/session_key作为业务访问凭据。

## 5. 接口与续期规则

| 接口 | 鉴权 | 行为 |
|---|---|---|
| GET /api/v1/capabilities | 公开 | 服务实现/配置能力；不能代替运行健康检查 |
| POST /api/v1/auth/wx-login | 微信code | 创建身份/会话；绝不自动上传健康数据 |
| POST /api/v1/auth/refresh | body中的refreshToken | 轮换refresh并返回新JWT |
| POST /api/v1/auth/logout | Bearer | 立即撤销当前会话 |
| GET /api/v1/me | Bearer | 最小账户信息；cloudSyncEnabled始终false |

accessToken有效15分钟，refresh会话绝对有效30天，按配置可调整；刷新不会延长绝对截止时间。每次业务请求除JWT验签外还查MySQL会话/账户状态，所以退出后旧JWT立即不可用。当前Redis负责登录/续期限流；Redis不可用时登录/续期失败，不放开限流绕过依赖。

**客户端必须串行刷新。** 同一refresh只能成功使用一次；再次出现会撤销会话以检测重放。刷新响应丢失或超时后不要盲目重放旧refresh，重新走wx.login；新code也不能当幂等凭据反复提交。若未来需要无感恢复，先设计有界重试协议，再改测试与契约。业务写接口的UUID幂等设计尚未实现。

错误包体有code/message/requestId/serverTime，登录凭据不进URL或普通日志。code失效401、未配置/供应商故障503、请求无效400/422、限流429。429一分钟后重试；401重新登录。生产禁用HTTP请求体和SDK调试日志采集。

## 6. 测试和打包

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/dev.ps1 -Task test
powershell -ExecutionPolicy Bypass -File ./scripts/dev.ps1 -Task package
```

默认运行纯令牌测试，不要求本机数据库。MySQL集成测试需要独立测试库和Redis：

```powershell
# 使用你自己的mysql连接方式创建：
# CREATE DATABASE cy_pregnancy_auth_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
$env:RUN_MYSQL_IT = 'true'
powershell -ExecutionPolicy Bypass -File ./scripts/dev.ps1 -Task test
Remove-Item Env:RUN_MYSQL_IT
```

集成测试使用`root/root`连接专用测试库，会删除该库的账号测试表内容；运行前代码校验SELECT DATABASE()必须是cy_pregnancy_auth_test。绝不把真实数据放进此库。微信网络边界在测试中替换，因此测试通过不代表真实AppID/code联调完成。Redis仅写带测试前缀/随机地址的计数键并自动过期，不执行FLUSHDB。

jar产物：`target/cy-pregnancy-server-0.1.0-SNAPSHOT.jar`。Jenkins 执行 mvn clean verify 后，Dockerfile 仅打包 JAR。部署步骤见[Jenkins 部署说明](../docs/DEPLOYMENT.md)。

## 7. 推荐阅读顺序

先看[代码阅读指南](docs/01-代码阅读指南.md)，再看AuthController → AuthService接口 → AuthServiceImpl → Mapper → SQL迁移；再看security下的TokenService、SessionValidityFilter及config下的SecurityConfig。各类注释说明职责，关键分支解释为什么这样做。

完整业务设计位于[后端评审目录](../docs/backend/README.md)；[后续开发清单](docs/03-后续开发清单.md)区分已实现和待实现。管理后台交接在[独立管理端目录](../cy-pregnancy-admin/README.md)。
