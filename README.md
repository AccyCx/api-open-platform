# 前言

​	为了巩固一些学到的java开发技术，于是我将它们综合之后创建了这个练手项目——API开放平台。本项目皆在提供一个类似阿里云API市场的开发者平台，不仅包含完整的前后端交互，更重点攻克网关限流、异步解耦与海量日志检索等后端核心痛点

# 一、项目背景与业务痛点分析

**1.核心业务**：管理员在后台发布各类 API 接口，开发者注册后申请调用密钥（AK/SK），并在自己的代码中集成调用。

**2.技术痛点**：

- **瞬间高并发**：如何防止恶意用户利用脚本疯狂刷接口，导致底层数据库宕机？
- **同步阻塞问题**：每次 API 调用都会产生计费和日志，如果同步写入数据库，接口响应会极度缓慢。
- **海量数据检索**：平台每天可能产生百万级以上的调用日志，如何在 C 端控制台让用户秒级查出带有特定条件的报错日志？

# 二、 技术栈选型与破局思路

为了解决上述痛点，本项目没有停留在传统的单体架构，而是引入了多项中间件：

- **底层基石：Spring Boot + MyBatis + MySQL** 

  负责核心元数据（用户信息、接口配置、密钥状态）的持久化与状态机流转。

- **极速缓存与限流防刷：Redis** 

  不直接查库，将用户的权限数据预热至 Redis。在 API 网关层结合 Lua 脚本实现高性能的“令牌桶算法”，对接口调用进行毫秒级限流。

- **异步解耦与削峰填谷：RocketMQ / Kafka** 网关处理完核心逻辑后，将“调用日志”打包发送至消息队列，立刻响应用户。后台消费者平滑拉取日志，彻底剥离耗时操作，保障 API 接口的高吞吐量。

- **海量文本秒级检索：ElasticSearch** 面对庞大且复杂的调用日志，放弃 MySQL 的低效模糊查询。将 MQ 消费出的日志批量存入 ES，利用倒排索引实现控制台的多维度、秒级报表查询。

- **并行处理优化：Java 多线程 (JUC)** 在后台生成用户调用统计报表时，使用自定义线程池与 `CompletableFuture` 实现多任务并行处理，大幅缩短定时任务的执行时间。

- **规范与工程化：Maven + Apifox + Swagger** 使用 Maven 进行标准的多模块划分；后端接口接入 Swagger 自动生成文档，并导入 Apifox 进行标准化的前后端接口联调。

- **前端展示：Vue3** 复用前端优势，搭建高颜值的数据可视化开发者控制台。

  # 三、 系统架构与多模块划分

  项目采用 Maven 多模块（Multi-Module）架构：

  - `api-platform-common`: 公共组件、全局异常、工具类。

  - `api-platform-model`: 统一的数据模型（Entity, DTO, VO）。

  - `api-platform-backend`: 核心管控后台，提供给前端的 RESTful 接口。

  - `api-platform-gateway`: **高并发核心**，基于 Spring Cloud Gateway，负责鉴权、路由、Redis 限流与 MQ 消息投递。

  - `api-platform-interface`: 模拟底层真实 API 接口服务。

  - `api-platform-client-sdk`: 为开发者封装的专属 Java 客户端 SDK，实现开箱即用的签名与调用。
  
  # 四、阶段规划
  
  - **阶段一**：系统架构设计、数据库表结构构建。
  
  - **阶段二**：核心管控后台开发与 Swagger/Apifox 联调。
  
  - **阶段三**：API 网关搭建、客户端 SDK 开发与签名认证（核心难点）。
  
  - **阶段四**：引入 Redis 限流、MQ 异步日志与 ES 报表检索。

# 数据库设计

## 1. 用户表 (`user`)

对于 API 开放平台，用户除了账号密码，最重要的是分配给他用来调用 API 的**“通行证”**（AccessKey 和 SecretKey）。

| **字段名**      | **类型** | **说明 **                                                    |
| --------------- | -------- | ------------------------------------------------------------ |
| `id`            | BIGINT   | 主键。                                                       |
| `user_account`  | VARCHAR  | 登录账号。                                                   |
| `user_password` | VARCHAR  | 登录密码（用 MD5+Salt 加密）。                               |
| `phone`         | VARCHAR  | 手机号，用于短信登录和找回密码。                             |
| `access_key`    | VARCHAR  | **核心**，API 调用的公钥（账号）。                           |
| `secret_key`    | VARCHAR  | **核心**，API 调用的私钥（密码，用于签名防篡改）。           |
| `user_role`     | VARCHAR  | 权限隔离：区分 `admin`（管理员，能发接口）和 `user`（普通开发者）。 |
| `create_time`   | DATETIME | 创建时间。                                                   |
| `update_time`   | DATETIME | 更新时间。                                                   |
| `is_delete`     | TINYINT  | **逻辑删除标志**（0表示正常，1表示已删除）。                 |

## 2.接口信息表 (`interface_info`)

这个表是提供给开发者调用的“商品货架”。不仅需要名字，还需要告诉网关怎么去请求这个接口。

| **字段名**        | **类型** | **说明 **                                                    |
| ----------------- | -------- | ------------------------------------------------------------ |
| `id`              | BIGINT   | 主键。                                                       |
| `name`            | VARCHAR  | 接口名称                                                     |
| `description`     | VARCHAR  | 接口描述。                                                   |
| `url`             | VARCHAR  | **核心** ，接口的真实调用地址。                              |
| `method`          | VARCHAR  | 请求类型（GET / POST 等），网关路由必须用到。                |
| `request_params`  | TEXT     | 请求参数说明（存 JSON 格式，方便前端渲染接口文档）。         |
| `request_header`  | TEXT     | 请求头说明。                                                 |
| `response_header` | TEXT     | 响应头说明。                                                 |
| `status`          | TINYINT  | 接口状态（0-关闭下线，1-正常上线），方便管理员随时停用出 Bug 的接口。 |
| `user_id`         | BIGINT   | 创建人 ID（记录是哪个管理员发布的）。                        |
| `create_time`     | DATETIME | 创建时间。                                                   |
| `update_time`     | DATETIME | 更新时间。                                                   |
| `is_delete`       | TINYINT  | 逻辑删除标志。                                               |

## 3.用户调用接口关系表 (`user_interface_info`)

在关系型数据库中，**永远不要在关系表里存“账户名”和“接口名”这类会变动的文本**，而是应该存它们的 `id`（外键思想）。从而消除数据冗余。

这个表主要用于记录**配额（剩余调用次数）**。

| **字段名**          | **类型** | **说明**                                                     |
| ------------------- | -------- | ------------------------------------------------------------ |
| `id`                | BIGINT   | 主键。                                                       |
| `user_id`           | BIGINT   | 调用者的用户 ID。                                            |
| `interface_info_id` | BIGINT   | 被调用的接口 ID。                                            |
| `total_num`         | INT      | 总调用次数（历史一共调了多少次，用于统计）。                 |
| `left_num`          | INT      | **核心**，剩余可调用次数（每次调用扣减 1，为 0 时 Redis/网关直接拦截）。 |
| `status`            | TINYINT  | 状态（0-正常，1-封号禁用）。比如发现某人恶意刷接口，单独封禁他调这个接口的权限。 |
| `create_time`       | DATETIME | 创建时间。                                                   |
| `update_time`       | DATETIME | 更新时间。                                                   |
| `is_delete`         | TINYINT  | 逻辑删除标志。                                               |

# API签名机制

很多人可能经常听到“调用API”，但是不知道API签名的运作原理，现在就让我们了解一下

API 调用通常是**机器对机器（代码对代码）**，且需要极高的防伪造能力。

- **AccessKey (AK)：** 相当于用户名（公开的）。告诉网关“我是谁”。
- **SecretKey (SK)：** 相当于密码（**绝对不能在网络上传输！**）。

**签名（Signature）的运作原理（就像对暗号）：**

1. **客户端（开发者）端：** 开发者把请求参数（比如想查哪个城市的天气）、加上随机数、加上当前时间戳，最后跟自己的 **SecretKey (SK)** 混在一起，用一种不可逆的加密算法（如 HMAC-SHA256），算出一个乱码字符串，这个乱码就是**“签名 (Sign)”**。
2. **发送请求：** 开发者把请求参数、AK、随机数、时间戳、**以及算好的签名(Sign)** 发给网关。（注意：这里面绝对不包含 SK）。
3. **服务端（网关）校验：** 网关拿到请求后，看到 AK，去数据库里查出这个 AK 对应的 SK 是什么。 然后，网关用收到的请求参数、随机数、时间戳，**结合查出来的 SK**，用同样的加密算法，自己也算出一个“签名”。
4. **比对结果：** 如果算出来的签名，和开发者发过来的签名**一模一样**，说明两件事：第一，他确实知道正确的 SK；第二，这个请求在半路上没有被黑客篡改过（如果参数被改了，算出来的签名一定不一样）。

*(注：加上随机数和时间戳，是为了防止黑客把正常的请求录下来，隔五分钟后再疯狂重发，这叫防重放攻击。)*

# 核心数据库SQL建表脚本

## 创建数据库

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS api_open_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE api_open_platform;
```

## 用户表

```sql
-- 1. 用户表 (开发者与管理员)
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_account` varchar(256) NOT NULL COMMENT '登录账号',
  `user_password` varchar(512) NOT NULL COMMENT '密码(加密后)',
  `phone` varchar(128) DEFAULT NULL COMMENT '绑定的手机号',
  `access_key` varchar(512) NOT NULL COMMENT 'API调用公钥(AK)',
  `secret_key` varchar(512) NOT NULL COMMENT 'API调用私钥(SK)',
  `user_role` varchar(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user-普通开发者, admin-管理员',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志(0-未删, 1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_account` (`user_account`),
  UNIQUE KEY `idx_access_key` (`access_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```



## 接口信息表

```sql
-- 2. 接口信息表 (API 商品货架)
CREATE TABLE `interface_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(256) NOT NULL COMMENT '接口名称',
  `description` varchar(512) DEFAULT NULL COMMENT '接口描述',
  `url` varchar(512) NOT NULL COMMENT '接口调用真实地址',
  `method` varchar(256) NOT NULL COMMENT '请求方法(GET/POST等)',
  `request_params` text COMMENT '请求参数说明(JSON格式)',
  `request_header` text COMMENT '请求头说明',
  `response_header` text COMMENT '响应头说明',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '接口状态(0-关闭, 1-开启)',
  `user_id` bigint(20) NOT NULL COMMENT '创建此接口的管理员ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志(0-未删, 1-已删)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口信息表';
```

## 用户调用接口关系表

```sql
-- 3. 用户调用接口关系表 (剩余调用次数与配额)
CREATE TABLE `user_interface_invoke` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '调用者的用户ID',
  `interface_info_id` bigint(20) NOT NULL COMMENT '被调用的接口ID',
  `total_num` int(11) NOT NULL DEFAULT '0' COMMENT '历史总调用次数',
  `left_num` int(11) NOT NULL DEFAULT '0' COMMENT '剩余可调用次数',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '调用状态(0-正常, 1-禁用此用户调用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志(0-未删, 1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_interface` (`user_id`,`interface_info_id`) COMMENT '联合索引提升查询速度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户接口调用关系表';
```



# 准备工作

## 搭建Maven微服务多模块骨架

![架构图](https://bu.dusays.com/2026/03/17/69b952b9c5b03.png)

## 连接数据库

![数据库连接](https://bu.dusays.com/2026/03/17/69b9581f7d6f4.png)

以及配置模块之间的关系、引入一些组件，到此为止本项目的环境搭建已经准备完毕，后续文章将会更新开发过程

我会将此项目上传到[我的GitHub](https://github.com/AccyCx/api-open-platform)，如果喜欢可以点个⭐！



# 项目开发阶段化

我将项目开发划分成了下面四个阶段，从基础准备到核心业务开发再到技术难点攻关

## 阶段一：基础与权限（当前阶段）

1. **统一返回类与异常处理**：填充common模块。
2. **数据模型映射**：在 `model` 模块里，根据 SQL 写出 `User`、`InterfaceInfo` 等实体类。
3. **用户模块 (User Service)**：
   - 实现**手机号注册/登录**（配合 Redis 存验证码）。
   - 引入 **JWT (Token)** 鉴权。
   - 实现 **AK/SK** 的自动生成（注册时分配）。

## 阶段二：接口管理（核心业务）

1. **接口发布与管理**：实现管理员对 API 接口的增删改查（CRUD）。
2. **接口详情页展示**：让开发者能在前端看到有哪些 API 可以调。

## 阶段三：签名认证与 SDK（技术难点）

1. **API 签名算法实现**：编写核心的校验逻辑。
2. **开发 SDK**：写一个可以让别人直接 `import` 的 jar 包，自动处理签名。

## 阶段四：网关与中间件（高并发攻坚）

1. **Gateway 搭建**：实现统一鉴权和路由转发。
2. **Redis 限流**：防止接口被刷。
3. **MQ + ES 日志系统**：异步收集调用记录并实现报表搜索。



# 统一返回类与异常处理

在一个标准的项目里，后端不能直接把一堆原始数据或者英文报错丢给前端。我们需要一个**统一的包装盒**，不管结果是成功还是失败，都长这样： `{ "code": 0, "data": { ... }, "message": "ok" }`

## 错误码枚举(`ErrorCode.java`)

```java
/**
 * 错误码枚举
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40301, "禁止操作"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
```

在这里采用的是业务状态码而不是HTTP状态码，因为标准的 HTTP 状态码只有几十个，不够描述复杂的业务场景。

- **404** 只告诉前端“资源找不到了”。
- **40400** 可能代表“找不到该用户”，**40401** 可能代表“找不到该订单”。 通过扩充位数（通常是 5 位），可以对错误进行**分类管理**。

另外，无论后端发生什么错误，通常都会给前端返回200OK的HTTP状态，然后在返回的JSON体中告知具体的业务代码：

```json
{
  "code": 40400,
  "message": "请求资源不存在",
  "data": null
}
```

这样做的好处是：前端的AJAX拦截器可以统一处理业务逻辑，而不会因为HTTP状态码不是200就直接崩溃或弹窗

## 通用对象返回(`BaseResponse.java`)

这就是那个“包装盒”，用泛型 `<T>` 确保它可以装下任何类型的返回数据。

```java
/**
 * 通用返回类
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;
    private T data;
    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
```

### 泛型的好处

**1.代码复用**：一套 `BaseResponse` 逻辑走天下，不用重复造轮子。

**2.类型安全**：编译器会帮你检查类型。如果你声明了 `BaseResponse<String>`，却试图往里面放个 `Integer`，代码在编译阶段就会报错，而不是等到程序运行（Runtime）时才崩溃。

**3.语义清晰**：看到 `BaseResponse<User>`，任何人一眼就能看出这个响应体里装的是用户信息。

### 常见的占位符字母

虽然你可以用任何字母（甚至是 `BaseResponse<ABC>`），但按照 Java 的惯例，通常使用以下单大写字母：

| **字母** | **含义**    | **常见用途**                     |
| -------- | ----------- | -------------------------------- |
| **T**    | **Type**    | 表示任意类型（最常用）           |
| **E**    | **Element** | 表示集合中的元素（如 `List<E>`） |
| **K**    | **Key**     | 表示键（如 `Map<K, V>` 中的键）  |
| **V**    | **Value**   | 表示值（如 `Map<K, V>` 中的值）  |
| **R**    | **Return**  | 表示方法的返回值类型             |

## 返回工具类（`ResultUtils.java`）

可以简化在Controller里的代码量，直接`ResultUtils.success(data)`就能返回标准格式，不需要每次都new

```java
/**
 * 返回工具类
 */
public class ResultUtils {

//    成功
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

//    失败
    public static BaseResponse error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

//    失败（自定义状态码和信息）
    public static BaseResponse error(int code,String message){
        return new BaseResponse<>(code,null,message);
    }

//    失败（综合枚举和自定义信息）
    public static BaseResponse error(ErrorCode errorCode,String message){
        return new BaseResponse<>(errorCode.getCode(),null,message);
    }

}

```

# 数据模型映射

主要使用了

1. **`Lombok (@Data)`**：自动生成 Get/Set 方法。
2. **`MyBatis-Plus` 注解**：告诉框架这个类对应哪张表、哪个是主键、哪个是逻辑删除字段。

## 用户实体类 (`User.java`)

这个类主要存储开发者的基本信息以及最重要的 API 签名密钥（AK/SK）。

```java
/**
 * 用户表
 */
@Data
@TableName(value = "user") //指定映射的数据库表名
public class User implements Serializable { //序列化，方便存入Redis和分布式调用

//    主键ID
    @TableId(type = IdType.AUTO) //指定主键生成策略为自增
    private Long id;

//    登录账号
    private String userAccount;

//    登录密码（加密存储）
    private String userPassword;

//    绑定的手机号
    private String phone;

//    API调用公钥（AK）
    private String accessKey;

//    API调用私钥（SK）
    private String secretKey;

//    用户角色：user-普通开发者，admin-管理员
    private String userRole;

//    创建时间
    private Date createTime;

//    更新时间
    private Date updateTime;

//    逻辑删除标志：0-未删除，1-已删除
    @TableLogic // 调用deleteById()，框架会自动变成 update is_delete = 1，而不是真删数据
    private Integer isDeleted;

    @TableField(exist = false) //这个字段在数据库表里不存在，不参与ORM映射
    private static final long serialVersionUID = 1L; //序列化版本号

}
```

## 接口信息实体类 (`InterfaceInfo.java`)

API 开放平台里的“商品货架”，记录了每个接口的详细属性。

```java
@Data
@TableName(value = "interface_info") //指定映射的数据库表名
public class InterfaceInfo implements Serializable {

//    主键ID
    @TableId(type = IdType.AUTO) //指定主键生成策略为自增
    private Long id;

//    接口名称
    private String name;

//    接口描述
    private String description;

//    接口调用真实地址
    private String url;

//    请求方法：GET、POST、PUT、DELETE等
    private String method;

//    请求参数说明(JSON格式)
    private String requestParams;

//    请求头说明
    private String requestHeader;

//    响应头说明
    private String responseHeader;

//    接口状态：0-关闭，1-开启
    private Integer status;

//    创建此接口的管理员ID
    private Long userId;

//    创建时间
    private Long createTime;

//    更新时间
    private Long updateTime;

//    逻辑删除标志：0-未删除，1-已删除
    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false) //这个字段在数据库表里不存在，不参与ORM映射
    private static final long serialVersionUID = 1L; //序列化版本号
}
```

## 用户调用接口关系表 (`UserInterfaceInvoke.java`)

高并发抢占资源的核心表,用来记录每个开发者对某个接口还能调用多少次（配额）

```java
@Data
@TableName(value = "user_interface_invoke") //指定映射的数据库表名
public class UserInterfaceInvoke implements Serializable {

    @TableId(type= IdType.AUTO)
    private Long id;

//    调用者的用户ID
    private Long userId;

//    被调用的接口ID
    private Long interfaceInfoId;

//    历史总调用次数
    private Integer totalNum;

//    剩余可调用次数
    private Integer leftNum;

//    调用状态（0-正常，1-禁用此用户调用）
    private Integer status;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L; //序列化版本号
}

```
