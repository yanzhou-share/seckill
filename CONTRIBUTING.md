# 贡献指南

## GitHub Flow 工作流

### 1. 克隆仓库
```bash
git clone https://github.com/your-username/seckill_test.git
cd seckill_test
```

### 2. 创建功能分支
```bash
git checkout -b feature/your-feature-name
```

分支命名规范：
- `feature/xxx` - 新功能
- `fix/xxx` - Bug修复
- `refactor/xxx` - 重构
- `docs/xxx` - 文档更新
- `test/xxx` - 测试

### 3. 开发并提交
```bash
# 开发完成后
git add .
git commit -m "feat: 添加xxx功能"
```

提交信息规范：
- `feat:` 新功能
- `fix:` Bug修复
- `refactor:` 重构
- `docs:` 文档
- `test:` 测试
- `chore:` 构建/工具

### 4. 推送到远程
```bash
git push origin feature/your-feature-name
```

### 5. 创建Pull Request
- 在GitHub上创建PR
- 填写PR模板
- 等待CI通过
- 请求代码审查

### 6. 合并
- 审查通过后合并到main分支
- 删除功能分支

## 本地开发

### 环境要求
- JDK 17+
- MySQL 8.0
- Redis 6.0+
- Maven 3.6+

### 启动应用
```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE seckill;"

# 2. 导入建表脚本
mysql -u root -p seckill < sql/init.sql

# 3. 启动Redis
redis-server

# 4. 启动应用
mvn spring-boot:run
```

### 运行测试
```bash
# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=SeckillControllerTest
```

## 代码规范

### Java
- 遵循Google Java Style Guide
- 使用Lombok简化代码
- 所有public方法添加JavaDoc

### Git
- 每个提交只做一件事
- 提交信息清晰描述变更内容
- 不要提交敏感信息(.env, 密钥等)

## CI/CD

### CI (持续集成)
- 每次push和PR自动运行
- 编译检查
- 单元测试
- 代码质量检查

### CD (持续部署)
- 推送tag时自动构建Docker镜像
- 镜像推送到Docker Hub
