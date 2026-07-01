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
| JWT | 0.9.1 | 认证 |
| Thymeleaf | - | 模板引擎 |
| Docker | - | 容器化部署 |

## 项目结构

```
seckill_test/
├── src/main/java/com/seckill/
│   ├── SeckillApplication.java          # 启动类
│   ├── config/                          # 配置类
│   │   ├── RedisConfig.java
│   │   └── WebMvcConfig.java
│   ├── common/                          # 公共模块
│   │   ├── Result.java                  # 统一返回结果
│   │   ├── Constants.java               # 常量定义
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── entity/                          # 实体类
│   │   ├── User.java
│   │   ├── Product.java
│   │   ├── SeckillActivity.java
│   │   └── Order.java
│   ├── mapper/                          # MyBatis Mapper
│   ├── service/                         # 业务层
│   ├── controller/                      # 控制层
│   ├── interceptor/                     # 拦截器
│   │   ├── JwtInterceptor.java
│   │   └── RateLimitInterceptor.java
│   └── util/
│       └── JwtUtil.java
├── src/main/resources/
│   ├── application.yml
│   └── templates/                       # 页面模板
├── src/test/java/com/seckill/           # 测试类
├── sql/init.sql                         # 建表脚本
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## 快速开始

### 方式一：Docker部署（推荐）

```bash
docker-compose up -d
```

访问 http://localhost:8080

### 方式二：本地部署

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

# 3. 修改配置（如需要）
vim src/main/resources/application.yml

# 4. 启动应用
mvn spring-boot:run
```

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
- Redis原子扣减库存
- 订单自动创建

### 5. 接口限流
- 滑动窗口限流
- 同一用户10秒内限1次请求

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
4. 重复购买检查 → 已购买返回错误
    ↓
5. Redis扣库存 → 库存不足返回错误
    ↓
6. 创建订单 → 返回秒杀成功
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

## Redis Key设计

| Key | 说明 | 类型 |
|-----|------|------|
| seckill:stock:{activityId} | 秒杀库存 | String |
| seckill:bought:{activityId} | 已购买用户 | Set |
| seckill:rate:{userId} | 请求限流计数 | String |

## 测试

```bash
# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=SeckillServiceTest
mvn test -Dtest=ConcurrencyTest
mvn test -Dtest=SeckillControllerTest
```

## 配置说明

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seckill
    username: root
    password: root
  redis:
    host: localhost
    port: 6379

jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时

seckill:
  rate-limit:
    window: 10      # 限流窗口(秒)
    max-count: 1    # 最大请求次数
```

## 页面说明

| 路径 | 说明 |
|------|------|
| /login | 登录/注册页 |
| / | 秒杀首页 |
| /admin | 管理后台 |
| /order | 我的订单 |

## License

MIT
