# 波波公考 (HongHu) - 公务员考试练习系统

课题题目为 **《基于 Spring Boot 和 Vue 的公务员考试在线学习与刷题系统的设计与实现》**。

<div align="center">

![波波公考登录页面](docs/screenshots/soybean-login.png)

**公务员考试在线学习与刷题平台**

支持刷题练习 | 模拟考试 | 申论批改 | 错题本 | 学习统计 | 讨论社区

[功能特性](#-功能特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [项目结构](#-项目结构) • [开发指南](#-开发指南)

</div>

---

## 📋 功能特性

### 用户端功能

- **学习首页** - 汇总真实答题、正确率、已提交考试和待复习错题，展示两张学习图表、六个学习入口与最近三条社区讨论。
- **模块刷题** - 按常识判断、言语理解与表达、判断推理、数量关系和资料分析选择题型与题量。
- **在线作答** - 提交答案后判题，显示正确选项和题目解析。
- **智能推荐** - 结合 IRT/Elo 能力与难度更新、SM-2 到期复习安排选题，提供薄弱优先、重点突破和冲刺提分三档模式。
- **模拟考试** - 展示可用试卷，支持计时作答、交卷评分和考试记录查询，成绩摘要展示得分、总分、实际用时及开始和交卷时间。
- **申论练习与评分** - 浏览申论题目和给定材料，输入答案，由本地评分逻辑按参考答案要点匹配给分。
- **错题本** - 自动收录错误作答，支持错题复习与掌握状态标记。
- **收藏夹** - 收藏题目与试卷，回顾重点内容。
- **学习统计** - 页面展示累计作答、正确率、答错次数与已提交考试数，并提供每日作答趋势、科目答题量分布、科目正确率和每日答题正确率图表。
- **讨论社区** - 发布讨论与回复，浏览列表和详情，删除自己的讨论与回复，支持点赞和答案采纳。

### 管理端功能

- **题库管理** - 检索、查看、添加、编辑和删除题目。
- **数据概览** - 查看用户、题库、答题与讨论数量，以及用户增长、活跃度、题型分布和近期活动。
- **社区管理** - 浏览讨论与回复，置顶或取消置顶讨论，以及删除讨论。

迁移脚本仅初始化知识点、公告和考试倒计时，不包含可直接练习的完整题库。题库需要使用 `tools/importer` 导入，并由使用者提供有权使用的题库素材。

### 界面与导航

界面采用用户选定的 Soybean 蓝紫学习工作台风格，统一登录、注册、考生端和管理端的配色、文字层级与卡片外观。桌面端使用白色左侧导航与顶栏；考生可从导航访问首页、模块刷题、模拟考试、申论练习、错题本、我的收藏、学习社区、学习统计和考试记录。移动端保留五个常用底部入口，其他页面通过顶栏菜单抽屉访问。

![蓝紫学习首页](docs/screenshots/soybean-home.png)

设计参考与改版记录见 [蓝紫学习工作台改版说明](docs/design/soybean-redesign.md)。

---

## 🛠 技术栈

### 系统架构

系统采用 **B/S 架构与前后端分离设计**。浏览器运行 Vue 单页应用，Spring Boot 单体后端提供 RESTful 接口，双方通过 JSON 交换数据。后端按 `Controller`、`Service`、`Mapper` 分层，由 MyBatis 访问 MySQL。

### 后端

- **Java 21、Spring Boot 3.3.3** - 请求处理与业务服务。
- **MyBatis、MyBatis Spring Boot Starter 3.0.3、PageHelper** - SQL 映射和分页查询。
- **MySQL 8、Flyway** - 数据存储和启动时数据库迁移。
- **JWT、BCrypt** - 登录令牌与密码哈希。
- **Caffeine / Redis** - 通过 `token.store` 选择服务端令牌存储，默认使用 Caffeine。
- **Springdoc OpenAPI 2.6.0** - Swagger 页面和 OpenAPI JSON。

### 前端

- **Vue 3、Vite 5** - 页面组件与开发构建。
- **Element Plus、Pinia、Vue Router** - 界面组件、状态和路由管理。
- **ECharts、Axios** - 图表与接口请求。
- **PWA** - 应用清单与静态资源缓存配置。

前端具体依赖版本由 `frontend/package-lock.json` 管理。后端依赖配置位于 `backend/pom.xml`。

---

## 🚀 快速开始

### 运行环境

项目使用 JDK 21、Node.js 18+ 与 MySQL 8。Windows 可使用仓库内的一键启动脚本；Maven Wrapper 要求 `JAVA_HOME` 指向有效 JDK 21。

| 用途 | 默认地址 |
| --- | --- |
| 前端及登录页面 | [http://localhost:7001/](http://localhost:7001/) |
| 管理端 | [http://localhost:7001/admin](http://localhost:7001/admin) |
| 后端健康检查 | [http://localhost:8080/api/health](http://localhost:8080/api/health) |
| Swagger 文档 | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/api-docs](http://localhost:8080/api-docs) |
| MySQL 数据库 | `localhost:3306/honghu` |

### 一键启动与停止

```powershell
.\一键启动.bat
```

在仓库根目录运行以上命令。一键启动检查已有服务，并尝试启动 Windows 服务 `MySQL80`。后端需要启动时先构建 JAR，前端需要启动时检查并安装缺少的 Vite 依赖，然后确认服务就绪。数据库服务名称和端口可按本机配置调整。

前后端在后台运行，输出保存在 `logs`。停止服务可双击 `停止服务.bat`，或在项目根目录运行以下命令。

```powershell
.\停止服务.bat
```

启动中的重复请求会收到提示；已经就绪的服务会被复用。

### VS Code 开发运行

在 VS Code 打开仓库文件夹，先停止已运行的前后端并确认 MySQL 运行，再使用两个 PowerShell 终端。以下命令从仓库根目录开始。

后端终端运行以下命令。

```powershell
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

前端终端运行以下命令。

```powershell
Set-Location .\frontend
npm.cmd install
npm.cmd run dev
```

### 登录与配置

普通考生通过“立即注册”创建账号。管理员在管理员账号不存在时由 `AdminInitializer` 创建，部署前应按项目实现设置并修改初始凭据。登录时选择对应角色。

普通考生通过“立即注册”创建账号。`AdminInitializer` 在管理员不存在时创建初始账号，管理员已存在时使用数据库中的现有账号。

数据库 URL、用户名和密码分别通过 `HONGHU_DB_URL`、`HONGHU_DB_USER`、`HONGHU_DB_PASSWORD` 配置。JWT 签名密钥通过 `HONGHU_JWT_SECRET` 提供，长度至少 32 字节。初始化环境和故障排查见 [QUICKSTART.md](QUICKSTART.md)，启停细节见 [启动说明.md](启动说明.md)。

---

## 📂 项目结构

```
honghu/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/honghu/
│   │   ├── controller/        # REST API 控制器
│   │   ├── service/           # 业务逻辑层
│   │   ├── mapper/            # MyBatis Mapper 接口
│   │   ├── pojo/              # 实体类 (POJO)
│   │   ├── common/            # 通用组件（Result、分页等）
│   │   ├── auth/              # 服务端令牌存储
│   │   ├── config/            # 配置与请求拦截器
│   │   ├── util/              # JWT 等工具
│   │   ├── annotation/        # 自定义注解
│   │   └── init/              # 启动初始化逻辑
│   ├── src/main/resources/
│   │   ├── db/migration/      # Flyway 数据库迁移脚本
│   │   └── application.properties
│   ├── mvnw / mvnw.cmd        # Maven Wrapper
│   └── pom.xml
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── views/             # 页面组件
│   │   │   ├── user/          # 用户端页面
│   │   │   └── admin/         # 管理端页面
│   │   ├── components/        # 可复用组件
│   │   ├── router/            # 路由配置
│   │   ├── store/             # Pinia 状态管理
│   │   ├── api/               # API 请求封装
│   │   └── assets/            # 静态资源
│   ├── public/                # 公共文件
│   ├── package.json
│   └── vite.config.js
├── tools/                      # 数据导入工具
│   └── importer/
├── docs/                       # 文档与截图
│   ├── design/                # 视觉参考与改版记录
│   └── screenshots/
├── 一键启动.bat                # Windows 快速启动脚本
├── 停止服务.bat                # Windows 停止服务脚本
├── start.ps1                   # 后台启动、数据库启动和就绪检查
├── stop.ps1                    # 项目进程停止
├── logs/                       # 服务运行日志
├── QUICKSTART.md               # 环境准备与快速启动
├── 启动说明.md                 # VS Code 与一键启停说明
└── README.md
```

---

## 💻 开发指南

### 后端开发

在 `backend` 目录使用 Maven Wrapper。

```powershell
Set-Location .\backend
.\mvnw.cmd package -DskipTests
.\mvnw.cmd test
```

开发运行使用 `.\mvnw.cmd spring-boot:run`。Java 代码或后端配置修改后重启后端；一键启动方式下，先停止前后端，再启动以重新构建 JAR。默认使用 Caffeine 保存有效令牌，后端重启后需要重新登录；代码更新后刷新浏览器页面。

### 前端开发

```powershell
Set-Location .\frontend
npm.cmd install
npm.cmd run dev
```

构建和预览使用以下命令。预览端口明确设为 `4173`。

```powershell
npm.cmd run build
npm.cmd run preview -- --port 4173
```

前端页面的 `/api` 请求由配置的代理访问后端。运行前核对后端地址与数据库连接。

Vite 开发服务器会更新前端源码和样式改动。首次加载改版界面时，按 Ctrl+F5 刷新浏览器；一键启动方式和访问端口保持原配置。

### 数据库迁移

后端启动时，Flyway 根据 `backend/src/main/resources/db/migration` 执行迁移。当前源码包含 `V1__Initial_Schema.sql`、`V2__Triggers_And_Procedures.sql`、`V3__Seed_Data.sql` 和 `V4__Exam_Snapshot_And_Recalc.sql`。迁移失败时根据后端日志和数据库迁移历史定位原因。

### 数据导入

`tools/importer` 的流程为 PDF 提取、题目解析、数据规范化与 MySQL 加载，依赖 Python 3.10+、`pdfplumber` 和 `PyMySQL`。将有权使用的素材放在一个本机目录，并通过 `HONGHU_IMPORT_SOURCE_ROOT` 指向该目录。仓库不包含题库 PDF、导入缓存或本机导入的数据。

以下命令用于查看已有参数。

```powershell
py -3 tools/importer/parse.py --help
py -3 tools/importer/load.py --help
py -3 tools/importer/build_shenlun_rubric.py --help
```

`load.py` 接受规范化 JSONL 输入和数据库连接参数，按输入更新题库，并下架输入中缺失的现有记录。执行加载前核对输入覆盖范围与目标数据库。`build_shenlun_rubric.py` 从参考答案生成评分要点，支持 `--dry-run` 查看处理数量。

导入过程的缓存、规范化结果与报告保存在 `tools/importer/build`。数据库连接参数可使用 `HONGHU_DB_HOST`、`HONGHU_DB_PORT`、`HONGHU_DB_USER`、`HONGHU_DB_PASSWORD` 和 `HONGHU_DB_NAME`。

### 接口文档

Swagger 页面与 OpenAPI JSON 由当前运行的后端生成。

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/api-docs)

常用接口包括以下路径。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/health` | 服务健康检查 |
| POST | `/api/auth/manager/login` | 管理员登录 |
| POST | `/api/auth/user/login` | 考生登录 |
| POST | `/api/auth/user/register` | 考生注册 |
| POST | `/api/practice/module/start` | 创建专项练习 |
| POST | `/api/practice/submit` | 提交作答并判题 |
| GET | `/api/exams/papers` | 查询可用试卷 |
| GET | `/api/exams/history` | 查询当前考生考试记录 |
| GET | `/api/shenlun/questions` | 查询申论题目 |
| POST | `/api/shenlun/questions/{id}/score` | 申论评分 |
| GET | `/api/admin/questions` | 管理员检索题库 |
| GET | `/api/stats/me` | 当前考生学习统计 |
| GET | `/api/stats/admin/overview` | 管理端数据概览 |

---

## 🎯 核心功能说明

### 题库与作答

题库覆盖五类行测模块和申论题。专项练习展示题干、选项和进度，提交后返回正确性、正确答案与解析。错题复习、收藏和考试记录由对应接口维护。

考试记录可从左侧导航或模拟考试页的“考试记录”标签打开，区分未交卷和已交卷状态。点击“成绩摘要”查看已有成绩信息；实际用时按秒记录，页面显示为分秒。未交卷记录不显示为零分成绩。

### 智能推荐

`PracticeService` 与 `MasteryService` 使用 IRT/Elo 更新能力和题目难度，结合 SM-2 复习到期时间选题。`WEAK`、`FOCUS`、`SECURE` 对应薄弱优先、重点突破和冲刺提分。用户数据不足时补充全库随机题目。

### 申论评分

`ShenlunScoringService` 在本地计算参考答案要点覆盖率，使用中文二元字组重叠和同义表述匹配，按要点累加分数。接口返回总得分、满分、要点命中明细和维度提示，页面展示题目要求、材料、作答区域、评分及维度反馈。

申论列表接口接受科目、题型、关键词和分页参数。前端可浏览题目卡片并使用关键词检索。

### 学习与管理统计

考生统计页面展示累计作答、正确率、答错次数与已提交考试数。答错次数为累计作答减去答对次数，已提交考试数仅统计已交卷的考试。

四张图表分别展示每日作答趋势、按作答题数计算的科目答题量分布、各科目正确率，以及每日答对次数除以作答次数得到的每日答题正确率。没有答题记录时，图表显示“暂无答题数据”。统计接口还提供平均考试分数。

图表随页面容器大小自动调整。科目正确率在只有一至两个科目时使用横向条形图，三个及以上科目时使用雷达图，便于阅读当前数据。

管理端提供注册用户、题库、答题、讨论与回复数量，以及增长趋势、活跃度、题型分布和最近活动。

### 讨论社区

讨论与回复页面按后端返回的列表、分页和记录标识读取内容，发布成功后更新列表，回复成功后刷新详情。详情展示作者名称与有效回复数。

---

## 页面接口对齐事项

当前代码中还存在以下页面对齐项。

- 管理端社区的“删除回复”和按置顶状态筛选仍缺少后端接口或参数支持。
- 申论列表接口支持 `subject`、`frQuestionType`、`keyword`、`pageNum` 和 `pageSize`，年份、地区控件的参数仍需与后端契约对齐。

---

## 🔒 安全说明

登录使用 JWT 签名令牌，服务端通过 `TokenStore` 登记有效令牌并支持注销。同一账号重新登录会撤销之前的令牌。`token.store` 默认选择 Caffeine；设置为 `redis` 并配置 Redis 连接后使用 Redis 令牌存储。

账号密码通过 BCrypt 哈希保存。角色访问由请求拦截器、`UserContext` 与管理员校验控制。SQL 参数由 MyBatis 绑定，页面文本使用 Vue 文本绑定展示。

签名密钥与有效期配置为 `jwt.secret`、`jwt.expiration`，令牌默认有效期为 43200 秒。数据库连接配置由 `spring.datasource.*` 提供。不要将数据库密码、JWT 密钥或管理员凭据提交到版本库。

发布前端构建产物时，HTTP 服务应将 history 路由返回到 `index.html`，并将 `/api` 请求转发至后端。服务器环境中的数据库连接、签名配置和访问代理按目标运行环境设置。

---

## 📊 题库数据

克隆仓库后，Flyway 会创建应用所需的表结构和基础知识点等记录。可练习的行测、申论题目和模拟试卷需从外部素材导入，且素材及其解析应由使用者自行确认授权。导入前应备份目标数据库，并核对输入文件覆盖范围，因为导入器会下架输入中缺失的既有记录。

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 📞 联系方式

- **项目主页**: https://github.com/123klh/honghu
- **问题反馈**: https://github.com/123klh/honghu/issues

---

## 🙏 致谢

感谢所有开源社区贡献的优秀项目：
- Spring Boot Team
- Vue.js Team
- Element Plus Team
- MyBatis Team
- 以及所有依赖项的维护者

---

**⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！**
