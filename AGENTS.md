# 秒杀系统 - 开发指南

## 项目概述
基于Spring Boot的秒杀系统，支持商品管理、秒杀活动、防刷限流、订单管理等功能。

## 技术栈
- Java 17
- Spring Boot 2.7.18
- MyBatis-Plus 3.5.3.1
- MySQL 8.0
- Redis
- JWT认证
- Thymeleaf模板
- Maven构建

## 项目结构
```
seckill_test/
├── src/main/java/com/seckill/
│   ├── SeckillApplication.java      # 启动类
│   ├── config/                      # 配置类(Redis、WebMvc)
│   ├── common/                      # 公共模块(Result、常量、异常处理)
│   ├── entity/                      # 实体类
│   ├── mapper/                      # MyBatis Mapper接口
│   ├── service/                     # 业务层
│   ├── controller/                  # 控制层
│   ├── interceptor/                 # JWT拦截器
│   └── util/                        # 工具类
├── src/main/resources/
│   ├── application.yml              # 配置文件
│   └── templates/                   # Thymeleaf页面
├── src/test/java/com/seckill/       # 测试类
└── sql/init.sql                     # 建表脚本
```

## 开发环境
- JDK 17+
- MySQL 8.0
- Redis 6.0+
- Maven 3.6+

## 常用命令
```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run

# 打包
mvn clean package -DskipTests
```

## 数据库
- 库名: seckill
- 建表脚本: sql/init.sql
- 测试库: seckill_test

## 核心流程
1. 用户注册/登录获取JWT Token
2. 管理员创建商品和秒杀活动
3. 开启活动后用户可参与秒杀
4. 秒杀流程: Token验证 → 限流 → 活动校验 → 重复购买检查 → Redis扣库存 → 创建订单

## Redis Key规范
```
seckill:stock:{activityId}    # 秒杀库存
seckill:bought:{activityId}   # 已购买用户集合
seckill:rate:{userId}         # 用户请求限流
```

## API接口
- `/api/user/register` - 注册
- `/api/user/login` - 登录
- `/api/product/*` - 商品管理
- `/api/seckill/*` - 秒杀活动
- `/api/order/*` - 订单管理
- `/admin` - 管理后台

## 测试说明
测试类位于src/test/java/com/seckill/:
- SeckillServiceTest: 业务逻辑测试
- ConcurrencyTest: 并发秒杀测试
- SeckillControllerTest: 接口集成测试
- RedisTest: Redis操作测试

## 注意事项
- 秒杀使用Redis预扣库存防止超卖
- 同一用户同一活动只能秒杀一次
- 接口限流: 同一用户10秒内只能请求1次
- 测试前需创建seckill_test数据库
