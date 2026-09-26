# 波波公考 - 快速启动指南

## 系统要求

- **JDK**: 21
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Maven**: 3.6+

## 启动步骤

### 1. 数据库初始化

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE honghu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出
exit;
```

### 2. 配置数据库连接

编辑 `backend/src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/honghu?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=你的密码
```

### 3. 启动后端

```bash
cd backend

# 方式1：使用Maven运行（开发模式）
mvn spring-boot:run

# 方式2：使用JAR包运行（生产模式）
mvn clean package -DskipTests
java -jar target/honghu-backend-1.0.1.jar
```

后端启动成功后访问：http://localhost:8080

API文档：http://localhost:8080/swagger-ui.html

### 4. 启动前端

```bash
cd frontend

# 安装依赖（首次运行）
npm install

# 开发模式启动
npm run dev

# 生产构建
npm run build
```

前端启动成功后访问：http://localhost:5173

## 默认账号

### 管理员账号
- 用户名：`admin`
- 密码：`admin123`

### 测试用户账号
- 用户名：`test`
- 密码：`test123`

*注意：首次启动时，后端会自动创建这些默认账号（InitRunner.java）*

## 常见问题

### 1. 后端启动失败

**问题**：找不到主类 `com.honghu.HonghuApplication`

**解决**：
```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

**问题**：数据库连接失败

**解决**：
- 确认MySQL服务已启动
- 检查数据库名称、用户名、密码配置
- 确认防火墙允许3306端口

### 2. 前端启动失败

**问题**：依赖安装失败

**解决**：
```bash
# 清除缓存重新安装
rm -rf node_modules package-lock.json
npm install
```

**问题**：API请求失败（跨域/404）

**解决**：
- 确认后端已启动在 http://localhost:8080
- 检查 `vite.config.js` 中的代理配置

### 3. 数据库表未创建

**问题**：表不存在错误

**解决**：
后端使用Flyway自动创建表结构，首次启动时会自动执行 `db/migration` 目录下的SQL脚本。

如果自动创建失败，手动执行：
```bash
mysql -u root -p honghu < backend/src/main/resources/db/migration/V1__init.sql
```

## 功能验证

### 1. 用户端功能

1. **注册/登录**
   - 访问 http://localhost:5173
   - 点击"注册"创建新账号或使用测试账号登录

2. **刷题练习**
   - 进入"刷题"页面
   - 选择题型和数量开始练习
   - 查看答题结果和解析

3. **模拟考试**
   - 进入"考试"页面
   - 选择试卷开始考试
   - 查看考试历史和成绩

4. **申论练习**
   - 进入"申论"页面
   - 选择题目作答
   - 查看AI评分结果

5. **错题本**
   - 自动收集错题
   - 标记复习状态

6. **数据统计**
   - 查看答题趋势
   - 题型正确率分布
   - 学习时长统计

### 2. 管理端功能

1. **登录管理后台**
   - 使用管理员账号登录
   - 自动跳转到 `/admin`

2. **数据概览**
   - 查看用户统计
   - 题库统计
   - 活跃度分析

3. **题库管理**
   - 添加/编辑/删除题目
   - 查看题目统计

4. **社区管理**
   - 置顶/删除讨论
   - 管理回复

## 数据导入

题库数据已通过导入工具从PDF中提取：

- **客观题**：7189道（已导入）
- **申论题**：100+道（已导入）

如需重新导入或更新题库，使用 `tools/importer` 工具。

## 生产部署

### Docker部署（推荐）

```bash
# 构建镜像
docker build -t honghu-backend ./backend
docker build -t honghu-frontend ./frontend

# 启动服务
docker-compose up -d
```

### 传统部署

**后端部署**：
```bash
# 打包
cd backend
mvn clean package -DskipTests

# 运行（使用nohup后台运行）
nohup java -jar target/honghu-backend-1.0.1.jar > app.log 2>&1 &
```

**前端部署（Nginx）**：
```bash
# 构建
cd frontend
npm run build

# 将 dist/ 目录内容部署到Nginx
cp -r dist/* /usr/share/nginx/html/

# 配置Nginx反向代理
# /etc/nginx/nginx.conf
location /api {
    proxy_pass http://localhost:8080;
}
```

## 性能优化建议

1. **数据库优化**
   - 添加合适的索引
   - 定期分析和优化查询
   - 考虑使用Redis缓存热点数据

2. **前端优化**
   - 启用gzip压缩
   - CDN加速静态资源
   - 图片懒加载

3. **服务器配置**
   - 调整JVM堆内存：`java -Xms512m -Xmx2048m -jar app.jar`
   - 配置连接池大小
   - 启用HTTP/2

## 技术支持

- 题库数据来源：真实公考试题
- 后端框架：Spring Boot 3.3.3
- 前端框架：Vue 3.5.12
- UI组件：Element Plus 2.8.4
- 数据可视化：ECharts 5.5.1

---

**祝您使用愉快！** 🎉
