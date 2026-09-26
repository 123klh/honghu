# 波波公考 — 新窗口交接提示词

> 本文件是交接文档，不是计划书。**接手方必须先读完本文，再读「真源文档」，然后才开始写代码。**
> 生成时间：2026-09-20。生成时状态：后端骨架已跑通并验证，题库导入器刚开工。

---

## 0. 交接一句话

后端可运行基线（鉴权 + 数据库 + 实体对齐）**已完成并实测通过**；
题库导入器**刚写完前 2 个文件（common.py / extract.py），一次都没跑过**；
题库里**一道题都还没有（question 表 0 行）**；前端目录是**空壳（0 个文件）**。
下一步是跑通导入器，把真题灌进库。

---

## 1. 项目目标

复刻并增强陆宽 2026 届本科毕业论文《基于 Java 的公考辅助 Web 系统的设计与实现》（原系统名「波波公考」）。

硬性要求（来自用户原始需求，不可打折）：

1. **必须真正可用**，不是骨架 demo。题库要有真实数据，业务流要能在浏览器里走通。
2. **必须比原论文更好**：修正原实现的 7 处缺陷 + 补齐原论文自述的 4 点不足。
3. 题库数据来自 `D:\复刻lk` 下的**真实公考 PDF**（国考/山东省考行测与申论真题、行测5000题、
   常识660篇等）。**不允许用假数据/占位数据凑数**。
4. 覆盖原论文全部功能点，不遗漏（管理端 + 用户端清单见真源文档「模块划分」）。

---

## 2. 真源文档（Source of Truth）

按优先级排列。**冲突时以序号小的为准。**

| # | 文档 | 位置 | 说明 |
|---|---|---|---|
| 1 | **实施计划** | `C:\Users\KLH\.claude\plans\5-26-2026-java-web-20260918111843-agent-parallel-falcon.md` | 唯一权威计划。含技术栈、数据库模型、7 项缺陷修正、4 项增强、题库导入策略、12 步实施步骤、8 条验证方式。**必读全文。** |
| 2 | **实际数据库 schema** | 运行 `mysql -uroot -p1234 honghu -e "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='honghu' ORDER BY TABLE_NAME, ORDINAL_POSITION;"` | **列名的唯一真源。** 见下方 §6「已知风险」第 1 条 —— 这是本项目已经踩过的最大坑。 |
| 3 | Flyway 迁移脚本 | `honghu/backend/src/main/resources/db/migration/V{1,2,3}__*.sql` | schema 的版本化定义。改 schema 必须新增 V4+，**不得修改已应用的 V1-V3**（Flyway 校验 checksum 会失败）。 |
| 4 | 原论文 PDF | `D:\复刻lk\½宽(5月26日 ...)基于Java的公考辅助Web系统的设计与实现_20260918111843.pdf` | 功能点清单与原实现细节的依据。已抽取文本在 `D:\复刻lk\_thesis_extract.txt`。 |
| 5 | 题库素材实测结论 | 真源文档「题库导入策略（已实测勘查）」章节 + `honghu/tools/importer/_probe_*.txt` | 哪些 PDF 可解析、哪些是扫描件、答案格式有几种。**已实测，不要重新猜。** |

**不存在项目级 CLAUDE.md，也没有已写入的 memory 文件。** 本 HANDOFF.md 与上表即全部约束来源。

---

## 3. Agent 宪法（下一步任务必须遵守）

### 3.1 工作方式

- **C1 全自主执行。** 用户已授权自主开发，消息通常只有「继续」。不要停下来问问题、不要等确认。
  遇到歧义：选最合理的方案，在代码注释里写明假设，继续做。
- **C2 完成的定义是「实测通过」，不是「代码写完」。** 每个阶段必须跑过真实验证（见 §5 验收标准）。
  报告结论时如实说明：跑过什么、通过什么、什么没验证。不得把「应该能work」说成「已完成」。
- **C3 同一错误连续失败 2 次就换路线。** 不要第三次微调同一处。先定位根因，说明失败原因，换根本不同的方案。
- **C4 修改前先读。** 任何实体/mapper/SQL 改动前，先查 §2 表里的真源 #2（实际 schema），不要凭记忆或凭论文字段名。

### 3.2 编码约束

- **C5 列名以实际 schema 为准，不以论文/直觉为准。** 见 §6 风险 1。
- **C6 不用 `ORDER BY RAND()`。** 抽题走「ID 池 + 应用层 Fisher-Yates 部分洗牌」，
  并保证同一场考试不出重复题。已在 `PracticeService.sample()` 实现，新代码复用它。
- **C7 `is_correct` 是布尔，`score_rate` 是得分率。** 不得混用（这是原论文缺陷 4 的修正点）。
- **C8 不引入外部大模型 API。** 申论判分必须本地可跑、无密钥依赖（真源文档 B.1）。
- **C9 密码只存 BCrypt 哈希。** 不得出现明文比对的登录 SQL。
- **C10 新增依赖需实际编译验证。** 本机 Maven 走 wrapper，可离线：`./mvnw -o`。

### 3.3 CJK / Windows 环境铁律（全部是已踩过的坑）

- **C11 Windows 控制台是 GBK。** Python 脚本**不要 print 中文**，会 `UnicodeEncodeError` 崩溃。
  人类可读输出一律写 UTF-8 文件（用 `common.Report`），只 print ASCII 进度。
- **C12 不用 `grep` 分析中文内容。** 对 UTF-8 CJK 不可靠。一律用 Python 正则。
- **C13 不要把 CJK 路径通过 `python -c` / heredoc 内联传参**，会 mojibake。
  一律在 `.py` 文件里用原始字符串基路径（`r'D:\复刻lk'`）+ `os.listdir` 拼接。
- **C14 curl 发中文 JSON 必须用 `--data-binary @file.json`**（文件用 Write 工具以 UTF-8 写）。
  内联 `-d '{"gender":"男"}'` 会被 shell 破坏，报 `Invalid UTF-8 middle byte`。
- **C15 目录名不要凭猜。** 源目录名极易看错（曾把 `2000-2024国考行测PDF` 误写成 `2000-2024年国考行测PDF`，
  多一个「年」字导致 `isdir=False`）。用 `_probe_paths.py` 那种 `os.walk` 清单确认真实路径。
- **C16 `os.path.isfile` 守卫。** 源数据里存在名字像 PDF 的**目录**（`24年国考行测（已完整）`）。
- **C17 pdfplumber 的 FontBBox 警告是良性的**，用 `warnings.filterwarnings('ignore')` 抑制。

### 3.4 构建与运行铁律

- **C18 不要用 `./mvnw spring-boot:run`。** 在此 CJK 路径下必然报
  `ClassNotFoundException: com.honghu.HonghuApplication`（已确认 class 文件存在、argfile classpath 正确，
  根因未解，已放弃）。**唯一可用方式**：
  ```bash
  cd "D:/复刻lk/honghu/backend"
  ./mvnw -q -o package -DskipTests -Dfile.encoding=UTF-8
  java -Dfile.encoding=UTF-8 -jar target/honghu-backend-1.0.1.jar > app.log 2>&1
  ```
- **C19 Windows 会锁住 target 里的 jar**，导致 `mvn clean` 报 `Device or resource busy`。
  绕法：先 `taskkill //F //PID <pid>`；仍失败则 bump `pom.xml` 的 `<version>`（当前已是 `1.0.1`）。
- **C20 起服务前先查端口**：`netstat -ano | grep ':8080.*LISTENING'`，
  有残留就 `taskkill //F //PID <pid>`。
- **C21 JDBC 不接受 `characterEncoding=utf8mb4`**（报 SQLState S1009）。
  连接串必须是 `characterEncoding=utf8&useUnicode=true`。**不要"修正"回 utf8mb4。**
- **C22 MyBatis 注解 mapper 与 XML mapper 不得定义同一 statement id**
  （报 `Mapped Statements collection already contains key`）。本项目已统一走注解，
  `mapper/ManagerMapper.xml` 已删除，不要加回。
- **C23 `@MapperScan("com.honghu.mapper")` 必须留在 `HonghuApplication` 上**，
  否则 `sqlSessionTemplate` bean 创建失败。

---

## 4. 已完成阶段 / 当前进度

### ✅ 阶段 1：骨架与可运行基线 —— 已完成，全部实测通过

真源文档「实施步骤」第 1 步 + 第 2 步（数据库迁移）+ 第 4 步（鉴权与账户）。

**已验证通过的事实**（本次会话实测，非推断）：

| 验证项 | 结果 |
|---|---|
| Maven Wrapper 离线编译 | `./mvnw -q -o compile` 零错误 |
| 打包 | `target/honghu-backend-1.0.1.jar`（50.8 MB） |
| 启动 | `Started HonghuApplication in 4.778 seconds`，Tomcat 8080 |
| Flyway | 3 个迁移已应用，schema version = 3 |
| 触发器 | 7 个已建（见下） |
| 存储过程 | 3 个已建：`sp_questions_by_difficulty` / `sp_random_questions` / `sp_user_stats` |
| `GET /api/health` | `{"code":200,...,"status":"UP"}` |
| `POST /api/auth/manager/login` | 200 + HS512 JWT，`role=SUPER_ADMIN` |
| `POST /api/auth/user/register` | 200；CJK 正确入库（`gender=男`, `address=山东济南`） |
| `POST /api/auth/user/login` | 200 + JWT |
| `GET /api/user/me` | 200，返回 principal |
| 无 token 访问 | 401 |
| **越权检查**（user token 打 `/api/admin/**`） | **403** ← 真源文档验证项 7 已过 |
| **单点登录**（B 登录踢掉 A） | **A 变 401，B 正常 200** ← 真源文档验证项 6 已过 |
| 登出后复用 token | 401 |
| 错误密码 | 401「用户名或密码错误」 |

已建触发器：`trg_question_after_insert` / `trg_question_after_delete` /
`trg_frquestion_after_insert` / `trg_frquestion_after_delete` /
`trg_answer_record_after_insert` / `trg_exam_score_after_insert` / `trg_exam_score_after_update`

**管理员凭据**：`admin` / `SeDDZ6GPxfLFVjy$`
（已落盘 `honghu/backend/ADMIN_CREDENTIALS.txt`。manager 表已有 row，
`AdminInitializer` 现在会短路跳过，不再生成新密码。想重新生成需先删 manager 行。）

### ✅ 本次会话的主要修复：实体/Mapper 全量对齐真实 schema

上一轮重构曾把实体字段名改成论文字段名，导致 `Unknown column` 系列崩溃。**已全部修正并编译通过**：

- **POJO 重写**（13 个）：`Manager`（`userName`→`managerName`，补 `status`/`descr`/`updatedAt`）、
  `User`（删 `phone`/`targetExamDate`/`targetScore`，补 `birthday`/`address`/`descr`/`updatedAt`）、
  `Question`（`questionContent`→`questionStem`、`correctAnswer`→`answer`、`answerExplanation`→`explanation`）、
  `AnswerRecord`、`ExamScore`、`Paper`、`FrQuestion`、`WrongBook`、`KnowledgePoint`（`pointId`→`kpId`）、
  `UserMastery`（改为 `masteryTheta`/`sm2Interval`/`sm2Ef`/`reviewDueAt`）、`QuestionDifficulty`、
  `Discussion`、`DiscussionReply`、`Announcement`、`OperateLog`、`ExamCountdown`、`ShenlunRubric`
- **Mapper 重写**（11 个）：ManagerMapper、UserMapper、QuestionMapper、ExamScoreMapper、
  AnswerRecordMapper、WrongBookMapper、KnowledgePointMapper、UserMasteryMapper、PaperMapper、
  ExamCountdownMapper、AnnouncementMapper；**新增** `QuestionDifficultyMapper`
- **新增 `MasteryService`**：IRT/Elo（`K_THETA=0.24`, `K_DIFFICULTY=0.02`）+ SM-2 间隔重复，
  含 `difficultyFromAccuracy()`（正确率 → IRT 难度 b）。**已编译，逻辑未经测试验证。**
- **`PracticeService` 重写**：ID 池 + Fisher-Yates 部分洗牌（替代 `ORDER BY RAND()`）、
  个性化组卷三档位（WEAK/FOCUS/SECURE）、错题本状态流转、多选题答案按字符集合比较。
  **已编译，未经端到端测试（因为题库为空，无法测）。**

### 🚧 阶段 3（进行中）：题库导入器 —— 刚开工，尚未运行

位置 `honghu/tools/importer/`。

**已勘查确认的事实**（实测，勿重猜）：

- 真实路径（注意：**没有「年」字**）：
  - 题干侧 A：`D:\复刻lk\2011-2025国考行测真题`（33 个 PDF）
  - 题干侧 B：`...\国考历年真题试卷（2000-2024带解析版）\2000-2024国考行测PDF（带解析版）\行测-真题`（39 个）
  - 答案侧：`...\2000-2024国考行测PDF（带解析版）\行测-答案及解析`（**39 个，已确认文件清单**）
  - 申论：`...\2000-2024国考申论PDF（带答案版）`（22）、`...\国考申论2000年-2023年（带答案版）`（25）
  - 常识660篇：`D:\复刻lk\《2024常识考点660篇》常识必备（21年8月-24年10月）.pdf`
- **答案侧有 3 种格式，解析器必须全部支持**（已在 `_probe_ans.txt` 实测确认）：
  1. `N、` 块 + `故正确答案为X。`（2019 等）
  2. `N.` 块 + `因此，选择X选项。`（2022 等）
  3. `第【N】题` + `正确答案:【B】` / `正确答案是：【B】`（部分年份）
- 题干侧格式：`N.题干` 后跟 `A.xx B.xx C.xx D.xx`（常在同一行），
  页眉形如 `· 第 N 页，共 M 页` 与重复的卷名行需剔除。
- 题干侧存在**占位题**：`4.题目正在全力以赴征集，将会在征集到后第一时间上传` + `A.缺失 B.缺失 C.缺失 D.缺失`
  → **必须进 rejects，不得入库。**
- 存在**康熙部首/异体字**污染：`录⽤`（⽤ U+2F64）、`⾏测`、`⼀`。normalize 阶段必须映射回正常汉字。

**代码进度**：

| 文件 | 状态 |
|---|---|
| `common.py` | ✅ 已写完。路径常量、`Report`（UTF-8 输出）、JSONL 读写、`paper_key()` 卷型归一（副省/地市/执法/A卷/B卷/卷一/卷二/单卷）、`list_pdfs()`（含 isfile 守卫） |
| `extract.py` | ✅ 已写完，**一次都没运行过**。PDF→文本 JSONL 缓存，按 path+size+mtime 做 cache key |
| `parse.py` | ❌ 未开始 |
| `normalize.py` | ❌ 未开始 |
| `load.py` | ❌ 未开始 |
| `_probe_*.py/.txt` | 勘查脚本与实测输出，**保留到导入完成后再清理**（是格式判断依据） |

**数据库现状（关键）**：
```
question=0  fr_question=0  paper=0  question_knowledge=0  knowledge_point=39  user=1  manager=1
```
知识点树 39 个已由 V3 种子数据建好（行测五大模块 + 申论，`parent_id=0` 表示根）。

### ❌ 完全未开始

实施步骤 5-12：题库/试卷管理 API、考试 API（全真模拟/小模考/自定义组卷）、
申论判分器、社区答疑、成语辨析、公告与倒计时后台化、
**前端（`honghu/frontend/` 只有空目录树：src/{api,assets,components,router,store,views}，0 个文件）**、
集成测试（`backend/src/test` 不存在）、PWA、Docker/Nginx 产物、README。

---

## 5. 下一步任务

### 立即任务：跑通导入器

1. `cd honghu/tools/importer && python extract.py` —— 首次运行，预计慢（828 页大 PDF）。
   读 `build/extract_report.txt` 核对各组文件数/字符数/扫描件数。
2. 写 `parse.py`：按源实现不同 parser；**跨文件夹配对**用 `common.paper_key()` 的 `(年份, 卷型)`。
   真源文档记录 2022 副省实测可端到端配对 **132/135**，可作为回归基线。
3. 写 `normalize.py`：NFKC、康熙部首→正常汉字、粗体重复字符去重（`公公务务员员`→`公务员`）、
   页眉页脚剔除（`· 第 N 页`、`公众号：`、`打开粉笔APP`、`.indd`）。
4. 写 `load.py`：以 `question_code`（源文件哈希+题号）为业务唯一键幂等入库，重跑不产生重复。
5. **质量闸门**：选项数==4、答案 ∈ A-D、题干非空、无占位「缺失」。
   未过闸的落 `build/rejects.jsonl`，**不静默丢弃**。

### 之后按真源文档「实施步骤」5→12 推进

---

## 6. 验收标准

### 导入阶段（当前阶段）

| # | 标准 | 如何验 |
|---|---|---|
| A1 | 客观题 ≥ 5,000 道**带答案**入库 | `SELECT COUNT(*) FROM question WHERE answer REGEXP '^[A-D]+$';` |
| A2 | 申论题 ≥ 100 道带参考答案 | `SELECT COUNT(*) FROM fr_question WHERE answer IS NOT NULL AND CHAR_LENGTH(answer)>50;` |
| A3 | `question_code` 无重复；**重跑导入器不增行** | 跑两次 `load.py`，比对 COUNT |
| A4 | 各题型分布合理（五大模块都有题） | `SELECT question_type, COUNT(*) FROM question GROUP BY question_type;` |
| A5 | **随机抽 20 题人工肉眼检查**题干/选项/答案干净可读 | 导出 UTF-8 文件后 Read（不要 print 中文到控制台） |
| A6 | rejects.jsonl 存在且被抽检过，占位题确实被拦下 | 读 `build/rejects.jsonl` |
| A7 | 不足量时**继续补源**，不得用假数据凑数 | — |

### 全项目（真源文档「验证方式」8 条，原文为准）

1. `./mvnw -o verify` 通过（含集成测试）；前端 `npm run build` 通过
2. Flyway 在**空库**上从零迁移成功；存储过程重算结果与触发器维护值一致（测试断言）
   —— ⚠️ 注意：当前只有 `sp_questions_by_difficulty`/`sp_random_questions`/`sp_user_stats`，
   **真源文档要求的 `sp_recalculate_*` 重算存储过程尚未创建**，需在 V4 迁移补上
3. 导入器实际入库并用 SQL 核对（= 上表 A1-A5）
4. 浏览器走完真实用户流：注册→登录→模块刷题→交卷看解析→错题进错题本→错题重练→
   已掌握流转→全真模拟（计时/答题卡/成绩）→个性化组卷→学习数据图表有真实数据→
   收藏夹→社区提问回答→申论作答看逐点反馈
5. 管理端：随机强密码首登→改密→题库 CRUD→用户管理→操作日志有记录→数据统计有数
6. ✅ **已过** 单点登录踢人
7. ✅ **已过** 越权返回 403
8. 第二浏览器/移动视口验证 PWA 可安装与移动端答题视图

**收尾**：清理 `_probe/`、`_thesis_extract.txt`、`app.log`、`_probe_*.txt/py`；
README 写明本机启动步骤。

---

## 7. 已知风险（接手方必读，均已发生过或已确认）

| # | 风险 | 状态 / 应对 |
|---|---|---|
| **R1** | **实体字段名与实际 schema 漂移** —— 本项目最大的坑。曾按论文字段名写实体，导致 `Unknown column 'real_name'` / `'user_name'` 等连环崩溃，耗掉大量时间 | 本轮**已全部修正并编译通过**。但**只有 Manager/User 路径经过运行时验证**；Question/Paper/AnswerRecord/WrongBook/UserMastery 等 mapper **从未真正执行过 SQL**（表是空的）。导入器灌数据后**第一次跑 practice 接口极可能再暴出列名问题**。改任何 SQL 前先查真源 #2 |
| **R2** | 题库为空 → `PracticeService`/`MasteryService` 全部逻辑**零运行时验证** | 导入完成后必须端到端跑一遍再宣称可用 |
| **R3** | `extract.py` **一次未运行**，可能有 bug（未验证的 import、未验证的 cache 逻辑） | 先跑它，别急着写 parse.py |
| **R4** | 大量源 PDF 是**纯扫描件（0 字符）**：申论100题各版本、2027行测5000题、资料分析1200题、花生十三各专项 | 无 OCR **不导入**。这直接压低 A1/A2 可达量，需靠真题配对 + 常识660篇 + 5000题达标 |
| **R5** | 真源文档要求的 `sp_recalculate_*` 重算存储过程**不存在** | 需新增 V4 迁移；不得改 V1-V3（checksum） |
| **R6** | 无 Redis（`token.store=caffeine` 单机模式）；无 Maven（走 wrapper）；Docker daemon 未运行 | Docker 产物只交付不本地验证；Redis 实现保留但走不通 |
| **R7** | `ADMIN_CREDENTIALS.txt` 含明文密码在仓库工作区 | 收尾阶段需删除；不要提交 |
| **R8** | `PracticeService.startPersonalizedPractice` 对**新用户**（无 user_mastery 行）走全库随机 fallback，题库空时会返回空列表 | 已知行为，导入后自然缓解 |
| **R9** | 当前有**后端进程在跑**（PID 67044，端口 8080），jar 被锁 | 重新打包前先 `taskkill //F //PID 67044` |
| **R10** | `target/` 下同时存在 1.0.0 与 1.0.1 两个 jar | 只用 **1.0.1**。1.0.0 是被锁住删不掉的残留 |
| **R11** | 非 git 仓库（`Is a git repository: false`），**无版本回退能力** | 大改前手动备份关键文件 |

---

## 8. 快速上手命令

```bash
# 查真实 schema（列名唯一真源）
mysql -uroot -p1234 honghu -e "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='honghu' ORDER BY TABLE_NAME, ORDINAL_POSITION;"

# 停掉在跑的后端
netstat -ano | grep ':8080.*LISTENING'
taskkill //F //PID <pid>

# 编译 / 打包 / 启动（唯一可用方式，勿用 spring-boot:run）
cd "D:/复刻lk/honghu/backend"
./mvnw -q -o compile -Dfile.encoding=UTF-8
./mvnw -q -o package -DskipTests -Dfile.encoding=UTF-8
java -Dfile.encoding=UTF-8 -jar target/honghu-backend-1.0.1.jar > app.log 2>&1 &

# 冒烟验证
curl -s http://localhost:8080/api/health
curl -s -X POST http://localhost:8080/api/auth/manager/login \
  -H "Content-Type: application/json" \
  -d '{"userName":"admin","password":"SeDDZ6GPxfLFVjy$"}'

# 发含中文的请求（必须走文件，勿内联）
curl -s -X POST http://localhost:8080/api/auth/user/register \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @_probe/register.json

# 跑导入器（下一步第一件事）
cd "D:/复刻lk/honghu/tools/importer" && python extract.py
```

**环境**：JDK 21.0.11（`D:\jdk21\jdk-21.0.11`）/ Node v24.17.0 / MySQL 8.0.45（root:1234, honghu）/
Python 3.11.3 + pdfplumber / 无 Maven（wrapper）/ 无 Redis / Docker 已装未运行。

---

## 9. 给接手方的第一条指令（可直接用作新窗口首条消息）

> 读 `D:\复刻lk\honghu\HANDOFF.md`，再读真源计划
> `C:\Users\KLH\.claude\plans\5-26-2026-java-web-20260918111843-agent-parallel-falcon.md`。
> 遵守 HANDOFF 的 Agent 宪法（尤其 C11-C23 的 CJK/Windows/构建铁律）。
> 当前任务：跑通 `honghu/tools/importer/extract.py`，然后依次实现 parse.py / normalize.py / load.py，
> 把真题灌进库，达到验收标准 A1-A7。全自主执行，不要问我问题。
