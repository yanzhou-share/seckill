# 秒杀系统

基于Spring Boot的高并发秒杀系统，支持商品管理、秒杀活动、防刷限流、订单管理等功能。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 2.7.18 | 基础框架 |
| MyBatis-Plus | 3.5.3.1 | ORM框架 |
| MySQL | 8.0 | 数据库 |
| Redis | 7.0 | 缓存/限流 |
| Redisson | 3.23.0 | 分布式锁 |
| JWT | 0.9.1 | 认证 |
| Thymeleaf | - | 模板引擎 |
| Docker | - | 容器化部署 |
| Kubernetes | - | 容器编排 |

## 项目结构

```
seckill_test/
├── src/main/java/com/seckill/
│   ├── SeckillApplication.java          # 启动类
│   ├── config/                          # 配置类
│   ├── common/                          # 公共模块
│   ├── entity/                          # 实体类
│   ├── mapper/                          # MyBatis Mapper
│   ├── service/                         # 业务层
│   ├── controller/                      # 控制层
│   ├── interceptor/                     # 拦截器
│   └── util/                            # 工具类
├── src/main/resources/
│   ├── application.yml
│   └── templates/                       # 页面模板
├── src/test/java/com/seckill/           # 测试类
├── sql/init.sql                         # 建表脚本
├── k8s/                                 # K8S部署配置
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── mysql.yaml
│   ├── redis.yaml
│   ├── hpa.yaml                         # 自动扩缩容
│   └── kustomization.yaml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## 快速开始

### 方式一：本地部署

**环境要求**
- JDK 17+
- MySQL 8.0
- Redis 6.0+
- Maven 3.6+

**步骤**
```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE seckill;"

# 2. 导入建表脚本
mysql -u root -p seckill < sql/init.sql

# 3. 启动应用
mvn spring-boot:run
```

### 方式二：Docker部署

```bash
docker-compose up -d
```

### 方式三：K8S部署

```bash
# 1. 构建镜像
docker build -t seckill:latest .

# 2. 部署
cd k8s
kubectl apply -k . -n seckill

# 3. 访问
kubectl port-forward -n seckill service/seckill-service 8080:80
```

## 环境变量

所有配置支持环境变量覆盖：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| SERVER_PORT | 服务端口 | 8080 |
| DB_URL | 数据库URL | jdbc:mysql://localhost:3306/seckill |
| DB_USERNAME | 数据库用户名 | root |
| DB_PASSWORD | 数据库密码 | (空) |
| REDIS_HOST | Redis主机 | localhost |
| REDIS_PORT | Redis端口 | 6379 |
| JWT_SECRET | JWT密钥 | - |
| JWT_EXPIRATION | JWT过期时间(ms) | 86400000 |
| RATE_LIMIT_WINDOW | 限流窗口(秒) | 10 |
| RATE_LIMIT_MAX_COUNT | 限流次数 | 5 |
| LOG_LEVEL | 日志级别 | INFO |

## 核心功能

### 1. 用户认证
- 用户注册/登录
- JWT Token认证
- 接口权限控制

### 2. 商品管理
- 商品CRUD
- 库存管理

### 3. 秒杀活动
- 活动创建/开启/关闭
- 时间范围控制
- Redis预扣库存

### 4. 秒杀下单
- 防重复购买
- Redis Lua原子扣减库存
- 异步队列创建订单

### 5. 接口限流
- Redis Lua脚本限流
- 同一用户10秒内限5次请求

### 6. 订单超时
- 30分钟未支付自动取消

## 秒杀流程

```
用户点击秒杀
    ↓
1. JWT认证 → 未登录返回401
    ↓
2. 接口限流 → 超限返回429
    ↓
3. 活动状态校验 → 未开始/已结束返回错误
    ↓
4. Redis Lua原子操作
   - 扣减库存
   - 检查重复购买
   - 标记已购买
    ↓
5. 入队异步创建订单 → 返回秒杀成功
```

## API接口

### 用户模块
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/register | 注册 |
| POST | /api/user/login | 登录 |
| GET | /api/user/info | 获取用户信息 |

### 商品模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/product/list | 商品列表 |
| GET | /api/product/{id} | 商品详情 |
| POST | /api/product | 创建商品 |
| PUT | /api/product | 更新商品 |
| DELETE | /api/product/{id} | 删除商品 |

### 秒杀模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/seckill/list | 进行中的活动 |
| GET | /api/seckill/listAll | 所有活动 |
| GET | /api/seckill/{id} | 活动详情 |
| POST | /api/seckill | 创建活动 |
| PUT | /api/seckill/{id}/status | 更新状态 |
| POST | /api/seckill/do/{id} | 执行秒杀 |
| GET | /api/seckill/check/{id} | 检查购买状态 |

### 订单模块
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/order/list | 订单列表 |
| GET | /api/order/{orderNo} | 订单详情 |
| PUT | /api/order/{id}/status | 更新状态 |

### 健康检查
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /actuator/health | 健康检查 |

## Redis Key设计

| Key | 说明 | 类型 |
|-----|------|------|
| seckill:stock:{activityId} | 秒杀库存 | String |
| seckill:bought:{activityId} | 已购买用户 | Set |
| seckill:rate:{userId} | 请求限流计数 | String |
| seckill:queue | 秒杀消息队列 | List |

## K8S部署

### 部署架构
- 3个应用副本（支持HPA自动扩缩）
- MySQL + 持久化存储
- Redis
- Ingress入口

### HPA自动扩缩
- CPU > 70% 扩容
- 内存 > 80% 扩容
- 最少2个Pod，最多10个Pod

### 常用命令
```bash
# 查看状态
kubectl get pods -n seckill

# 查看HPA
kubectl get hpa -n seckill

# 查看日志
kubectl logs -f -l app=seckill -n seckill

# 扩缩容
kubectl scale deployment seckill --replicas=5 -n seckill
```

## 测试

```bash
# 运行核心测试（推荐）
mvn test -Dtest=SeckillServiceTest
mvn test -Dtest=ConcurrencyTest

# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=RedisTest
mvn test -Dtest=RedissonTest
mvn test -Dtest=SeckillControllerTest
```

### 测试用例说明

| 测试类 | 说明 | 用例数 |
|--------|------|--------|
| SeckillServiceTest | 业务逻辑测试 | 11 |
| ConcurrencyTest | 并发秒杀测试 | 2 |
| RedisTest | Redis操作测试 | 7 |
| RedissonTest | 分布式锁测试 | 5 |
| SeckillControllerTest | 接口集成测试 | 11 |
| UserControllerTest | 用户接口测试 | 9 |
| ProductControllerTest | 商品接口测试 | 9 |

## 页面说明

| 路径 | 说明 |
|------|------|
| /login | 登录/注册页 |
| / | 秒杀首页 |
| /admin | 管理后台 |
| /order | 我的订单 |

## License

MIT
