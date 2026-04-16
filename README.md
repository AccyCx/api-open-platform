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

# 用户模块

## 注册模块

### 密码加密工具类 (`PasswordUtils.java`)

这里采用最经典的 **MD5 + 固态盐值（Salt）** 方案。Spring 框架自带了非常好用的 `DigestUtils`，我们直接拿来用。

```java
/**
 * 密码加密工具类
 */
public class PasswordUtils {

//    盐值（Salt），用于混淆密码
//    随便写一段复杂的字符串，不能泄漏给外部
    private static final String SALT = "api_platform_AccyCx_2026";

    /**
     * MD5 加密带盐密码
     * @param userPassword 用户在前端输入的明文密码
     * @return 加密后的32位密文
     */
    public static String encryptPassword(String userPassword){
//        将明文密码和盐值拼接在一起，增加复杂度
        String saltedPassword = SALT + userPassword;
//        使用spring自带的工具类转化为MD5十六进制字符串
        return DigestUtils.md5DigestAsHex(saltedPassword.getBytes());
    }

}
```

为什么要“加盐（Salt）”？

单纯的 MD5 加密并不安全，因为黑客手里有‘彩虹表’（记录了常见密码 `123456` 对应的 MD5 值）。为了防破解，应该在明文密码上拼接了一段只有后端代码才知道的‘盐值（Salt）’。这样一来，即使用户的密码再简单，经过加盐混淆后，算出来的 MD5 值也是完全陌生且无法通过彩虹表反查的。

(注：在更高级的安全场景中，还会使用 `BCrypt` 这种每次生成密文都不一样的动态加盐算法，目前我们用 MD5+静态盐已经足够支撑这个项目的注册登录逻辑了。)

### 密钥生成工具类 (`KeyUtils.java`)

这里我们使用 Java 自带的 `UUID`（通用唯一识别码）来生成基础的随机串。为了让 SK 更加安全和复杂，我们甚至可以复用刚才写的 `PasswordUtils` 对它进行一次哈希混淆。

```java
/**
 * API密钥生成工具类
 */
public class KeyUtils {

    /**
     * 生成AccessKey
     * 特点：必须全局唯一，使用去掉横岗的UUID
     *
     * @return 32位随机字符串
     */
    public static String generateAccessKey(){
        return UUID.randomUUID().toString().replace("-","");
    }

    /**
     * 生成SecretKey
     * 特点：必须全局唯一，还要足够复杂防破解
     * 方案：生成一个UUID，然后套一层MD5加密，增加复杂度
     *
     * @return 32位复杂哈希字符串
     */
    public static String generateSecretKey(){
//        先生成一个基础的随机UUID
        String rawKey = UUID.randomUUID().toString().replace("-","");
//        复用PasswordUtils 进行加盐MD5混淆
        return PasswordUtils.encryptPassword(rawKey);
    }

}

```

###  建立数据通道：`UserMapper.java`

在 `mapper` 包下新建这个接口。它继承了 `MyBatis-Plus` 的 `BaseMapper`，连一行 SQL 都不用写，就已经拥有了对 `user` 表的增删改查能力。

```java

/**
 * 用户表 Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
```

### 定义业务规范：`UserService.java`

在 `service` 包下新建这个接口。同样继承 `MyBatis-Plus` 的 `IService`。我们在这里定义一个专门用于注册的方法。(之前提到有手机号注册，`redis`存验证码的方案，后续会添加)

```java
/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账号
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);
}
```

### 核心逻辑落地：`UserServiceImpl.java`

这里详细定义注册方法的功能：

```java
/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public long userRegister(String userAccount,String userPassword,String checkPassword){

//        1.校验参数是否为空
        if(StringUtils.isAnyBlank(userAccount,userPassword,checkPassword)){
            throw new RuntimeException("参数不能为空");
        }

//        2.账号长度不能小于4位，密码不能小于8位
        if(userAccount.length()<4 || userPassword.length()<8){
            throw new RuntimeException("账号过短或密码过短");
        }

//        3.校验两次输入的密码是否一致
        if(!userPassword.equals(checkPassword)){
            throw new RuntimeException("两次输入的密码不一致");
        }

//        4.检查账号是否重复（数据库里查）
//        注意:高并发场景下这里其实是不够的，必须配合数据库 user_account 字段的唯一索引来防重
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account",userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if(count>0){
            throw new RuntimeException("账号重复");
        }

//        5.密码加密
        String encryptPassword = PasswordUtils.encryptPassword(userPassword);

//        6.颁发API调用的AK/SK
        String accessKey = KeyUtils.generateAccessKey();
        String secretKey = KeyUtils.generateSecretKey();

//        7.将数据插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setAccessKey(accessKey);
        user.setSecretKey(secretKey);

//        MyBatis-Plus 的 save 方法会自动填充 createTime 和 updateTime 字段
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new RuntimeException("注册失败，数据库错误");
        }

        return user.getId();
    }
}
```

第 4 步检查账号重复，如果在极高的并发下，两个请求同时来到这里，发现账号都不存在，然后同时执行插入，不就产生重复账号了吗？

没错！代码层面的查重在多线程下会失效。所以我们在之前设计数据库表时，已经在 `user_account` 字段上加了 **`UNIQUE KEY`（唯一索引）**。即使代码层没拦住，数据库底层也会抛出 `DuplicateKeyException`，确保数据绝对一致。

### 创建注册请求DTO

```java
/**
 * 用户注册请求体
 */
@Data
@Schema(description = "用户注册请求体")
public class UserRegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

//    账号
    @Schema(description = "用户账号")
    private String userAccount;

//    密码
    @Schema(description = "用户密码")
    private String userPassword;

//    确认密码
    @Schema(description = "确认密码")
    private String checkPassword;
}
```

### 编写 UserControlle的用户注册接口

```java
**
 * 用户接口
 */
@RestController  //标注这是一个 RESTful 控制器，返回 JSON 数据
@RequestMapping("/user") //接口基础路径
@Tag(name = "用户接口", description = "用户的注册、登录与管理")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     *
     * @param userRegisterRequest 封装了前端传来的账号、密码、确认密码
     * @return 统一返回格式，包含新注册用户的ID
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest){
//        1.校验请求体是否为空
        if(userRegisterRequest == null){
//            使用封装的统一错误码返回
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }

//        2.提取参数
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

//        3.Controller层做一层基础的非空校验（Service层做深度业务校验）
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "账号、密码或确认密码不能为空");
        }

//        4.调用Service层执行真正的注册落库逻辑
        long result = userService.userRegister(userAccount, userPassword, checkPassword);

//        5.将结果包装成标准格式返回给前端
        return ResultUtils.success(result);
    }

}
```

到现在：一条完整的“注册”业务线已经彻底打通！前端发起 HTTP POST 请求 -> `UserController` 接收并校验 -> `UserService` 加密并分配 AK/SK -> `UserMapper` 插入数据库。成功通过接口测试后就可以进行下一步了！

## 登录模块

### 创建JWT工具类

在 `common` 模块的 `utils` 包下创建 `JwtUtils.java`。这个工具负责根据用户的 ID 和账号，生成一段加密的字符串（Token）。

在写这个工具类前，记得在pom文件里面添加JWT相关的依赖

```java
public class JwtUtils {
    // Token过期时间，这里设置为7天
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    // JWT 签名密钥（必须满足新版 HS512 的安全长度要求）
    private static final String SECRET_KEY = "api_platform_jwt_secret_key_accycx_must_be_very_long_for_security_reasons_123456";

    // 将字符串秘钥转换成安全规范的Key对象
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    public static String generateToken(Long userId, String userAccount){
        Map<String,Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userAccount", userAccount);

        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(KEY, SignatureAlgorithm.HS512)
                .compact();
    }
}
```

### 创建登录 DTO 和登录返回的 VO

```java
@Data
@Schema(description = "用户登录请求体")
public class UserLoginRequest implements Serializable {

    @Schema(description = "用户账号")
    private String userAccount;

    @Schema(description = "用户密码")
    private String userPassword;
}

```

登录成功后，前端不仅需要 Token，还需要展示用户的账号和角色。我们绝对不能把包含 AK/SK 的原声 `User` 类直接扔给前端，所以要用一个脱敏的 VO 包装一下。

```java
@Data
@Schema(description = "登录用户返回体")
public class LoginUserVO implements Serializable {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户账号")
    private String userAccount;

    @Schema(description = "用户角色")
    private String userRole;

    @Schema(description = "令牌")
    private String token;//颁发给前端的令牌
}

```

### 在Service中实现登录逻辑

在`UserService.java`中添加方法定义：

```java
LoginUserVO userLogin(String userAccount, String userPassword);
```

在`UserServiceImpl.java`中实现：

```java
//    用户登录逻辑
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword){
//        1.校验费控
        if(StringUtils.isAnyBlank(userAccount,userPassword)){
            throw new RuntimeException("账号和密码不能为空");
        }

//        2.密码加密(将前端传来的明文密码进行加密，再去和数据库里的比对)
        String encryptPassword = PasswordUtils.encryptPassword(userPassword);

//        3.查询数据库是否存在该用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account",userAccount);
        queryWrapper.eq("user_password",encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if(user == null){
            throw new RuntimeException("账号或密码错误");
        }

//        4.账号密码正确，生成JWT Token
        String token = JwtUtils.generateToken(user.getId(),user.getUserAccount());

//        5.封装返回脱敏数据（VO）
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setToken(token);

        return loginUserVO;

    }
```

### 在Controller写登录接口

在`UserController.java`中实现：

```java
    /**
     * 用户登录接口
     *
     * @param userLoginRequest 封装了前端传来的账号和密码
     * return 统一返回格式，包含登录用户的基本信息和令牌
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest){

//        1.校验请求体是否为空
        if(userLoginRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }

//        2.提取参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

//        3.Controller层做一层基础的非空校验（Service层做深度业务校验）
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }

//       获取包含Token的完整登录信息
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword);

        return ResultUtils.success(loginUserVO);
    }
```



到这里该项目的一阶段已完成，下篇文章会进入到阶段二：接口管理（核心业务）

# 接口管理——实现CRUD

## 第一步：打通底层数据通道 (Mapper & Service)

### 创建 `InterfaceInfoMapper.java`

```java
/**
 * 接口信息表 Mapper 接口
 */
public interface InterfaceInfoMapper extends BaseMapper<InterfaceInfo> {
}
```

### 创建 `InterfaceInfoService.java`

```java
/**
 * 接口信息服务
 */
public interface InterfaceInfoService extends IService<InterfaceInfo> {
    // 稍后会在这里定义校验逻辑方法
    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add);
}
```

## 第二步：定义交互契约 (DTO)

新增和修改的参数是不同的（新增不能传 ID，修改必须传 ID），所以绝对不能混用一个实体类。

### 新增接口请求体：`InterfaceInfoAddRequest.java`

```java
/**
 * 新增接口请求体
 */
@Data
public class InterfaceInfoAddRequest implements Serializable {

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

    @Serial
    private static final long serialVersionUID = 1L; //序列化版本号
}

```

### 修改接口请求体：`InterfaceInfoUpdateRequest.java`

```java
/**
 * 更新接口请求体
 */
@Data
public class InterfaceInfoUpdateRequest implements Serializable {


//    主键（更新时必须传ID，否则数据库不知道更新哪一条）
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
    @Serial
    private static final long serialVersionUID = 1L; //序列化版本号
}

```

因为通常删除一条数据只需要传一个主键`id`，所以可以给所有模块写一个通用的删除请求体，所有的模块，只要是删除操作，统一复用这个类

### 通用删除请求体：`DeleteRequest.java`

```java
/**
 * 通用删除请求体
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 主键 ID
     */
    private Long id;

    @Serial
    private static final long serialVersionUID = 1L;
}
```

骨架和契约搭好了，下一步就是写Service层，完成刚刚提到的参数校验，然后在Controller层写增删改查的完整HTTP接口

## 第三步：核心业务逻辑与 Controller

### 编写 Service 实现类（参数校验层）

不管前端有没有做校验，后端在向数据库执行“新增”或“修改”前，必须进行严格的字段合法性检查（比如接口名字不能太长，URL 不能为空）

```java
/**
 * 接口信息服务实现类
 */
@Service
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo> implements InterfaceInfoService {

    @Override
    public void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add){

//        1.校验参数是否为空
        if(interfaceInfo == null){
            throw new RuntimeException("接口信息不能为空");
        }

//        2.接收参数
        String name = interfaceInfo.getName();
        String url = interfaceInfo.getUrl();
        String method = interfaceInfo.getMethod();

//        如果是新增操作（add为true），所有必填参数不能为空
        if(add){
            if(StringUtils.isAnyBlank(name,url,method)){
                throw new RuntimeException("接口名称、URL和请求方法不能为空");
            }
        }

//        无论是新增还是修改，都要校验业务规则（比如名字不能太长）
        if(StringUtils.isNotBlank(name) && name.length()>50){
            throw new RuntimeException("接口名称过长");
        }

    }
}

```

### 编写 Controller：`InterfaceInfoController.java`

```java
/**
 * 接口管理 API
 */
@RestController
@RequestMapping("/interfaceInfo")
@Tag(name = "接口管理",description = "管理员和用户对API接口的增删改查")
public class InterfaceInfoController {

    @Autowired
    private InterfaceInfoService interfaceInfoService;

    // TODO: 这里还需要引入 UserService 获取当前登录用户的 ID，目前我们先写死或跳过.等后面完善网关拦截再补充

    /**
     * 创建接口
     */
    @PostMapping("/add")
    @Operation(summary = "发布新接口")
    public BaseResponse<Long> addInterfaceInfo(@RequestBody InterfaceInfoAddRequest interfaceInfoAddRequest){

        if(interfaceInfoAddRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

//        DTO转实体类
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoAddRequest,interfaceInfo);

//        校验参数
        interfaceInfoService.validInterfaceInfo(interfaceInfo,true);

//        这里我们先写死一个用户ID，后续完善了用户系统后再改成动态获取
        interfaceInfo.setUserId(1L);

        boolean result = interfaceInfoService.save(interfaceInfo);
        if(!result){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR,"创建接口失败");
        }
        return ResultUtils.success(interfaceInfo.getId());
    }

    /**
     * 删除接口
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除接口")
    public BaseResponse<Boolean> deleteInterfaceInfo(@RequestBody DeleteRequest deleteRequest){
        if(deleteRequest == null || deleteRequest.getId() <= 0){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR);
        }
        boolean result = interfaceInfoService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);

    }

    /**
     * 更新接口
     */
    @PostMapping("/update")
    @Operation(summary = "更新接口")
    public BaseResponse<Boolean> updateInterfaceInfo(@RequestBody InterfaceInfoUpdateRequest interfaceInfoUpdateRequest){
        if(interfaceInfoUpdateRequest == null || interfaceInfoUpdateRequest.getId()<=0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoUpdateRequest,interfaceInfo);

//        校验参数（非新增参数）
        interfaceInfoService.validInterfaceInfo(interfaceInfo,false);

        boolean result = interfaceInfoService.updateById(interfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据ID查询接口详细信息
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取接口详细信息")
    public BaseResponse<InterfaceInfo> getInterfaceInfoById(Long id){
        if(id <= 0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        return ResultUtils.success(interfaceInfo);
    }
}


```

# 模拟第三方接口提供服务

在引入“签名认证”和“网关”之前，先写几个真实的、毫无防备的“裸体 API”跑通一下，新建另外一个干净的模块：**`api-platform-interface`**（模拟第三方接口提供者的服务）。

### 第一步：配置接口服务的基础环境

这个模块相当于一个独立的小项目，我们需要给它配置 Web 环境和独立的端口号，防止和我们的主后台（8101 端口）冲突。

**1. 添加 Web 依赖 (`pom.xml`)** 打开 `api-platform-interface` 的 `pom.xml`，确保里面有 Spring Boot Web 依赖：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

**2. 配置文件 (`application.yml`)** 在 `src/main/resources` 下新建 `application.yml`：

```yaml
server:
  port: 8102 # 接口服务跑在 8102 端口
```

### 第二步：编写三个经典的测试接口

这里写三种最常见的传参方式：**GET 请求传参、POST 请求 URL 传参、POST 请求 JSON 传参。**

**1. 准备一个接收 JSON 的实体类** 在 `apiinterface` 包下新建一个 `model` 包，创建一个极简的 `User.java`（注意，这不是主后台用来存数据库的那个 User，这只是用来接收 JSON 参数的一个极其简单的对象）：

```java
@Data
public class User {
    private String username;
}
```

**2. 编写 `NameController.java`** 在 `apiinterface` 包下新建 `controller` 包，创建这个核心的测试控制器：

```java
/**
 * 名称 API
 * 提供查询名称的接口
 */
@RestController
@RequestMapping("/name")
public class NameController {

    // 1. GET 方式请求，参数在 URL 上（比如 /name/get?name=jingxuan）
    @GetMapping("/get")
    public String getNameByGet(String name) {
        return "GET 你的名字是：" + name;
    }

    // 2. POST 方式请求，参数在 URL 上或表单里
    @PostMapping("/post")
    public String getNameByPost(@RequestParam String name) {
        return "POST 你的名字是：" + name;
    }

    // 3. POST 方式请求，参数在请求体 (JSON) 里面
    @PostMapping("/user")
    public String getUserNameByPost(@RequestBody User user) {
        return "POST JSON 你的名字是：" + user.getUsername();
    }
}
```

### 第三步：启动并测试

1.测试 GET 接口:

直接在浏览器输入地址：`http://localhost:8102/name/get?name=AccyCx`

测试结果如图：

![测试结果](https://bu.dusays.com/2026/04/05/69d26d49b1419.png)

2.测试POST接口（用`Apifox`）：

（1）发送 POST 请求到 `http://localhost:8102/name/post?name=AccyCx`

测试结果如图：

![测试结果](https://bu.dusays.com/2026/04/05/69d26d8a44d09.png)



（2）发送 POST 请求到 `http://localhost:8102/name/user`，并在 Body 中选择 `raw` -> `JSON`，输入 `{"username": "AccyCx"}`。

测试结果如图：

![测试结果](https://bu.dusays.com/2026/04/05/69d26dccb7ea5.png)

测试成功之后就说明已经顺利跑通了，但是现在这种模拟服务有一个致命漏洞，那就是：我现在只要知道这个 `http://localhost:8102/name/user` 的地址，任何人、任何黑客都可以无限次地通过 Postman 来调用它！根本不需要经过我主平台的同意，也根本没办法扣除调用次数，所以接下来第三阶段会做：API 签名认证（AK/SK 防护）。

调用者每次发请求，都必须在请求头（Header）里带上根据他的 `SecretKey` 算出来的一串复杂“签名”。接口这边验证签名通过了，才允许执行并返回结果。



# 签名认证

为什么要做签名认证？

如果你直接把接口暴露出去，黑客可以通过抓包拿到请求地址，然后用脚本疯狂刷你的接口，导致你的服务器瘫痪，甚至把你的数据库拖垮

**防守策略（AK/SK 机制）：**

1. **AK (AccessKey)**：是公开的，代表“你是谁”。每次请求都要带在请求头（Header）里。
2. **SK (SecretKey)**：是绝密的，代表“你的密码”。**绝对不能放在请求头里在网络上传输！**
3. **Sign (签名)**：客户端在发请求前，把请求参数和绝密的 `SK` 拼接在一起，用 MD5 等算法算出一串“乱码”（这就是签名）。
4. **验证**：服务端收到请求后，拿到明文的 `AK`，去数据库查出对应的 `SK`。然后服务端用同样的参数和 `SK` 再算一次签名。如果两次签名一致，说明请求确实是这个用户发出的，且参数没有被篡改。

## 实现签名生成算法

### 第一步：打造签名生成算法

我们先回到 `api-platform-common` 模块，把这个签名算法写成一个通用的工具类，这样以后不管是客户端发请求，还是服务端做校验，都可以复用。

```java
/**
 * API签名工具类
 */
public class SignUtils {
    /**
     * 生成API调用签名
     *
     * @param body 请求体内容（或者请求参数）
     * @param secreKey 用户的私钥
     * @return 经过MD5加密的签名字符串
     */
    public static String genSign(String body,String secreKey){

//        防止明文拼接被破解，可以在body和secretKey之间加入一个固定的分隔符，增加破解难度
        String content = body+ "." + secreKey;

        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
//            TODO:一般还会把随机数（Nonce）和时间戳（Timestamp）加入签名拼接中，以此防范“重放攻击”
//             目前先用最精简的 body + SK 跑通主流程，后面做网关拦截时可以再加固
    }
}

```

#### 第二步：定义标准的请求头契约

为了让签名机制生效，客户端每次调用我们的接口，都必须在 HTTP 请求头（Header）里携带以下四个关键信息：

1. `accessKey`：标识调用者身份。
2. `nonce`：随机数（防止重放攻击）。
3. `timestamp`：时间戳（防止请求过期）。
4. `sign`：利用我们刚才的工具类算出来的签名。

到这里签名算法工具就准备好了，接下来就是在服务端写一个拦截器，专门去扒取请求头里的签名，并去数据库里查信息对比

## 实现服务端拦截器

接下来，需要在 `api-platform-interface` 模块里建立这道“安检门”，标准步骤如下：

1. **拦截请求**：在 HTTP 请求到达 `NameController` 之前，强制把它拦下。
2. **扒取请求头**：从 Header 中提取出调用方传来的 `accessKey`、`nonce`、`timestamp` 和 `sign`。
3. **风控基础校验**：
   - 防过期：判断 `timestamp` 是不是超过了 5 分钟？（防止黑客拿着几天前的请求一直刷）。
   - 防重放：判断 `nonce`（随机数）是不是在短时间内已经被用过了？
4. **核心签名校验**：根据 `accessKey` 去数据库查出这个用户的 `secretKey`。服务端用同样的参数和查到的私钥再算一遍签名，如果算出来的结果和传过来的 `sign` 严丝合缝，安检放行；否则，直接报“无权限”踢出。

在实现拦截器时，一般有两个注意点：

1. 这个拦截动作通常会放在**统一网关（Gateway）**里做，而不是每个具体的微服务自己做。
2. 校验签名时，需要去数据库查 `SecretKey`。但我们的 `api-platform-interface` 是一个模拟第三方接口的独立模块，它没有连接主数据库。

所以为了跑通核心的**签名算法和防刷逻辑**，这个阶段先在这个 `interface` 模块里写一个本地拦截器，并且在代码里**Mock**一个正确的 AK 和 SK。等到了项目第四阶段（引入 Gateway 和 RPC），我们会把这段逻辑无缝迁移到网关，并连上真实的数据库。

### 第一步：引入 Common 模块依赖

我们需要用到刚才在 `common` 模块写的 `SignUtils` 工具类，添加下面的依赖：

```xml
<dependency>
            <groupId>com.accycx</groupId>
            <artifactId>api-platform-common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

### 第二步：编写核心安全拦截器 (Interceptor)

在 `api-platform-interface` 的`apiinterface` 下新建包 `interceptor`，然后创建 `ApiAuthInterceptor.java`。

这段代码包含了防伪造、防重放、防过期的风控逻辑：

```java

/**
 * API 调用全局权限拦截器
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

//    模拟数据库中查出来的分配给这个用户的真实AK和SK
    private static final String MOCK_AK = "accycx_test_ak";
    private static final String MOCK_SK = "accycx_test_sk";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception{
//        1.从请求头中扒取调用方带过来的凭证
        String accessKey = request.getHeader("accessKey");
        String nonce = request.getHeader("nonce");
        String timestamp = request.getHeader("timestamp");
        String sign = request.getHeader("sign");
        String body = request.getHeader("body");

//        2.校验AK是否存在及合法
        if(accessKey == null || !accessKey.equals(MOCK_AK)){
            throw new RuntimeException("无权限：AccessKey 错误或不存在");
        }

//        3.防重放：校验随机数（简单版）
//        一般做法：把nonce存进Redis，如果发现这个nonce已经存在，说明是黑客在重放攻击，直接拒绝
//        这里先简单校验长度，大于4位即可
        if(nonce == null || nonce.length() <4){
            throw new RuntimeException("无权限：非法请求（Nonce 不合法）");
        }

//        4.防过期：校验时间戳
        if(timestamp == null){
            throw new RuntimeException("无权限：缺少时间戳");
        }

//        计算当前时间与请求时间的差值（设定请求有效期为5分钟）
        long currentTime = System.currentTimeMillis() / 1000;
        final long FIVE_MINUTES = 5 * 60;
        if((currentTime - Long.parseLong(timestamp)) >= FIVE_MINUTES){
            throw new RuntimeException("无权限：请求过期");
        }

//        5.校验签名
//        服务端使用同样的body和查出来的真实SK再算一遍签名
        String serverSign = SignUtils.genSign(body,MOCK_SK);

//        比对调用方传来的签名和算出来的签名是否一致
        if(sign == null || !sign.equals(serverSign)){
            throw new RuntimeException("无权限：签名校验失败，数据可能被篡改！");
        }

//        所有安检通过，放行请求，进入对应的Controller
        return true;
    }
}

```

这段代码采用了 Timestamp + Nonce 的双重防御。首先判断 Timestamp 是否过期（通常是 5 分钟），拦截掉旧请求。然后将 Nonce（随机数）存入 Redis 并设置 5 分钟过期时间。每次请求来时去 Redis 查，如果 Nonce 已经存在，说明是重放攻击，直接拒绝。这两者结合，保证了安全又不会撑爆 Redis 内存。

当然现阶段还没有引入Redis，所以到阶段四的时候会完善。

### 第三步：注册拦截器并挂载到接口上

拦截器写好了，但 Spring Boot 还不知道要把这扇门安在哪里。我们需要写一个配置类，告诉系统：“所有访问 `/**` 的请求，都必须走这个安检门。”

在 `apiinterface` 包下新建包 `config`，创建 `MvcConfig.java`：

```java
/**
 * Web MVC 配置类
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private ApiAuthInterceptor apiAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
//        将拦截器注册到Spring MVC中
        registry.addInterceptor(apiAuthInterceptor)
                .addPathPatterns("/**");//拦截所有进入此服务的请求
    }
}

```

现在重新启动接口服务并访问之前测试过的简单的地址：

http://localhost:8102/name/get?name=accycx

会抛出500异常，控制台会打印出写的报错信息：“无权限：`AccessKey`错误或不存在”，这就说明拦截器生效了

# SDK

## 模拟用户使用接口服务

由于手写底层的 `HttpURLConnection`太过繁琐，所以我们在这里引入**Hutool**工具包

### 第一步：引入 Hutool 工具包

在`api-platform-interface` 模块引入依赖：

```xml
<dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.8.16</version>
        </dependency>
```

### 第二步：编写客户端调用类 (`ApiClient.java`)

在 `api-platform-interface` 模块的`apiinterface` 下新建一个包 `client`，然后创建 `ApiClient.java`：

```java
/**
 * 调用第三方接口的客户端类
 */
public class ApiClient {

    private final String accessKey;
    private final String secretKey;

//    构造方法，强制要求调用者传入AK和SK
    public ApiClient(String accessKey,String secretKey){
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 核心逻辑：组装请求头
     * 把凭证全部放在Header里
     */
    private Map<String,String> getHeaderMap(String body){
        Map<String,String> hashMap = new HashMap<>();
        hashMap.put("accessKey",accessKey);

//       生成随机数（防重放）
        hashMap.put("nonce", RandomUtil.randomNumbers(4));

//        生成当前时间戳（防过期）
        hashMap.put("timestamp",String.valueOf(System.currentTimeMillis() / 1000));

//        将请求体参与签名计算
        hashMap.put("body",body);

//        生成签名
        hashMap.put("sign", SignUtils.genSign(body,secretKey));

        return hashMap;
    }

//    1.调用GET接口
    public String getNameByGet(String name){
//        Hutool的HttpUtil可以简化HTTP请求的发送
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.get("http://localhost:8102/name/get",paramMap);
        System.out.println(result);
        return result;
    }

//    2.调用POST URL传参接口
    public String getNameByPost(String name){
        HashMap<String,Object> paramMap = new HashMap<>();
        paramMap.put("name",name);
        String result = HttpUtil.post("http://localhost:8102/name/post",paramMap);
        System.out.println(result);
        return result;
    }

//    3.调用POST JSON接口（携带签名）
    public String getUserNameByPost(User user){
//        将User对象转为JSON字符串
        String json = JSONUtil.toJsonStr(user);

//        发送带请求头的HTTP请求
        HttpResponse httpResponse = HttpRequest.post("http://localhost:8102/name/user")
                .addHeaders(getHeaderMap(json)) //关键：把算好的签名头放进去
                .body(json) //塞入请求体
                .execute();

        System.out.println(httpResponse.body());
        String result = httpResponse.body();
        System.out.println(result);
        return result;

    }
}
```

### 第三步：测试安检门

在 `api-platform-interface` 的 `src/test/java` 目录下建一个 `Main.java` 测试类，编写测试代码：

```java
public class Main {
    public static void main(String[] args) {
        // 1. 填入在拦截器里 Mock 好的真实 AK 和 SK
        String accessKey = "accycx_test_ak";
        String secretKey = "accycx_test_sk";
        // 2. 实例化客户端
        ApiClient apiClient = new ApiClient(accessKey, secretKey);
        // 3. 准备参数
        User user = new User();
        user.setUsername("accycx");
        // 4. 发起带有签名验证的请求
        System.out.println("----- 测试开始 -----");
        String result = apiClient.getUserNameByPost(user);
        System.out.println("服务端返回结果"+ result);
    }
}

```

启动接口服务，并运行测试类，得到结果：

![测试结果](https://bu.dusays.com/2026/04/08/69d6380e09845.png)

故意把 `String secretKey = "accycx_test_sk";`改错后再测试，后台会返回错误信息：

![测试结果](C:\Users\86173\AppData\Roaming\Typora\typora-user-images\image-20260409113938157.png)

## 开发SDK

为什么要开发 SDK？

刚才我们为了测试接口，在 `Main` 方法里手动组装了 Header，手动算了 Sign。 试想一下，如果你的平台有 1000 个开发者接入，难道你要让这 1000 个人每个人都去研究你的签名算法，然后各自写一遍 `SignUtils` 和 `ApiClient` 吗？ 如果他们算错了哪怕一个字符，就会一直报 500 错误，然后疯狂找你对线。

解决方法：官方提供一个 **SDK（比如一个定制的 Spring Boot Starter）**。开发者只需要引入这个依赖，在 `application.yml` 里填上你发给他的 AK/SK，剩下的签名计算、请求头拼接，SDK 全部在底层自动搞定。开发者只需要写一行代码 `apiClient.getUserNameByPost(user)` 就能拿到数据。

### 第一步：创建独立的 SDK 模块

由于 SDK 是要打成 jar 包发给别人用的，它必须是一个干净、纯粹的模块，所以在根工程下，新建一个模块，命名为 **`api-platform-client-sdk`**。

1. 配置 `pom.xml`

这是 SDK 的核心依赖，注意，我们要引入 `spring-boot-configuration-processor`，这是做自定义 Starter 的灵魂，它能让使用 SDK 的人在 `application.yml` 里敲代码时拥有自动提示功能。

```xml
  <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.8.16</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

2. 搬运核心资产 (代码大迁移)

为了让 SDK 独立运行，我们需要把刚才在其他模块写的几个类**原封不动**地复制到 `api-platform-client-sdk` 的 `src/main/java/com/jingxuan/apiclientsdk` 包下：

1. `User.java` (专门接收参数的小模型)
2. `SignUtils.java` (签名工具)
3. `ApiClient.java` (客户端)

然后把`api-platform-interface`包下的`ApiClient.java`删了

### 第二步：编写自动装配类 (`AutoConfiguration`)

我们要写一段配置代码，让 Spring Boot 在启动时，自动读取配置文件里的 AK/SK，然后自动帮我们创建一个 `ApiClient` 实例扔进 Spring 容器里（`@Bean`）。

**1. 创建属性配置类** 

在 `apiclientsdk` 包下新建 `client` 包，创建 `ApiClientConfig.java`：

```java
/**
 * ApiClient 自动配置类
 */
@Configuration
//这个注解的意思是：去 application.yml 里读取前缀为 "api.client" 的配置，映射到这个类的属性上
@ConfigurationProperties("api.client")
@Data
@ComponentScan
public class ApiClientConfig {

    private String accessKey;
    private String secretKey;

    /**
     * 将ApiClient 注入到Spring容器中国
     */
    public ApiClient apiClient(){
//        使用配置文件中读取到的ak和sk实例化客户端
        return new ApiClient(accessKey, secretKey);
    }
}

```

**2. 注册自动配置类** 

由于我们是要做一个给别人用的 jar 包，别人项目启动时，默认只会扫描他们自己包下的类，根本扫不到我们写的 `ApiClientConfig`。

在 `src/main/resources` 目录下，**依次**新建三个嵌套文件夹：`META-INF` -> `spring`。 最终路径为：`src/main/resources/META-INF/spring`。

在这个文件夹下，新建一个文本文件，名字必须叫：**`org.springframework.boot.autoconfigure.AutoConfiguration.imports`** *

在这个文件中，写入刚才写的配置类的全限定名：

```
com.accycx.apiclientsdk.client.ApiClientConfig
```

完成这两步之后，API开放平台客户端SDK就大功告成了，下面在interface模块里面引入它测试一下

### 模拟测试

#### 第一步：将 SDK 打包并安装到本地仓库

现在写的SDK只是普通代码，我们需要把它变成一个 `.jar` 包，并放到电脑本地的 Maven 仓库（`.m2` 文件夹）里，这样其他模块才能引用它。

在maven面板里面给SDK模块`clean`再`install`一遍，SDK就发布好了。

**为什么要点 `install` 而不是 `package`？** 

因为 `package` 只是把 jar 包打在当前模块的 `target` 目录下；而 `install` 会把打好的 jar 包安装到本地的 Maven 仓库中，让整台电脑里的其他项目都能通过 `pom.xml` 搜到并使用它。

#### 第二步：在测试模块引入 SDK

现在回到之前测试用的 **`api-platform-interface`** 模块，我们要把刚刚自己写的 SDK 像引入 Spring Boot 官方组件一样引进来。

打开 `api-platform-interface` 模块的 `pom.xml`，在 `<dependencies>` 中加入：

```xml
        <dependency>
            <groupId>com.accycx</groupId>
            <artifactId>api-platform-client-sdk</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

#### 第三步：在配置文件中填入凭证（极简配置）

打开 `api-platform-interface` 模块的 `src/main/resources/application.yml`，在最下面加上我们在 SDK 里定义好的配置前缀：

```yaml
api:
  client:
    access-key: jingxuan_test_ak
    secret-key: jingxuan_test_sk
```

#### 第四步：使用 Spring Boot Test 测试

既然交给了 Spring Boot 自动装配，我们就不能用普通的 `main` 方法测试了，因为普通的 `main` 方法没有启动 Spring 容器。我们需要用 Spring Boot 的单元测试。

在 `api-platform-interface` 的 `src/test/java/com/accycx/apiinterface` 目录下，新建（或修改已有的）测试类 `ApiInterfaceApplicationTests.java`：

```java
@SpringBootTest
class ApiPlatformInterfaceApplicationTests {

    // 这个没有写任何 new ApiClient() 的代码，直接注入就能用
    // 因为我们写的 SDK 里的 AutoConfiguration 已经在后台帮我们把 application.yml 里的密钥塞进去并实例化了。
    @Resource
    private ApiClient apiClient;

    @Test
    void contextLoads() {
        // 1. 准备参数
        User user = new User();
        user.setUsername("AccyCx");

        // 2. 一行代码，直接调用！SDK 在底层会自动算好签名、拼好请求头并发送。
        String result = apiClient.getUserNameByPost(user);

        System.out.println("测试结果：" + result);
    }
}
```

点击测试，可以看到控制台成功打印了结果，如图

![测试结果](https://bu.dusays.com/2026/04/12/69dbb4e6e7e31.png)

到这里就说明我们的自定义SDK已经大功告成了！

但是，我们刚才只是在`interface` 模块里“本地模拟”了第三方开发者。这只证明了我们的SDK能发请求，`interface` 能拦请求，整个平台的闭环还没有打通。

- **缺失的拼图一：动态分配凭证。** 现在的 AK/SK 都是我们为了跑通主流程在代码里写死的（`accycx_test_ak`）。真实情况是，每个用户注册后，会在主平台的数据库里生成自己独一无二的 AK/SK。
- **缺失的拼图二：真实的验签逻辑。** 我们的 `interface` 服务现在是在本地拦截器里写死了AK、SK 来对比。真实的业务是：`interface` 服务收到请求后，必须通过**某种方式**，拿着用户传过来的 `accessKey`，去**主数据库**里查出对应的 `secretKey`，才能完成验签。

**所以，下一步要进行：主后台与SDK联动。**

## 主后台与SDK联动

我们要把刚才写好的、纯净无暇的 SDK 接入到主业务模块 **`api-platform-backend`** 中，让主后台真正拥有调用 API 的能力。

未来的业务场景：

管理员可以在主平台的管理界面上，直接点击一个“在线测试调用”按钮，主后台底层就会使用这个 SDK，自动带着管理员的 AK/SK 去调用 `interface` 模块里的真实接口，并把结果展示在页面上！

### 第一步：在主业务后台引入 SDK

打开 **`api-platform-backend`** 模块的 `pom.xml`，把我们的轮子装上去：

```xml
		<dependency>
			<groupId>com.accycx</groupId>
			<artifactId>api-platform-client-sdk</artifactId>
			<version>1.0-SNAPSHOT</version>
		</dependency>
```

### 第二步：给主后台配置“管理员”凭证

为了让主后台能代表平台方去“在线测试”接口，我们需要给主后台配置一套凭证（这也是我们 SDK 自动装配必须的）。

打开 `api-platform-backend` 模块的 `src/main/resources/application.yml`，在底部添加：

```yaml
api:
  client:
    access-key: accycx_admin_ak
    secret-key: accycx_admin_sk
```

第三步：编写“在线调用”业务逻辑

我们在之前的`InterfaceInfoController`，增加一个“在线测试调用”的接口。

**1. 新建在线测试请求体 (DTO)** 在 `api-platform-model` 模块下的 `com.accycx.model.dto.interfaceinfo` 包中，新建 `InterfaceInfoInvokeRequest.java`：

```java
/**
 * 接口调用请求体
 */
@Data
public class InterfaceInfoInvokeRequest implements Serializable {

//    接口主键id
    private Long id;

//    用户传入的测试参数（如果是JSON格式，那就是那一串JSON字符串）
    private String userRequestParams;

    @Serial
    private static final long serialVersionUID = 1L;
}

```

**2. 增加调用 Controller 接口** 回到 `api-platform-backend` 模块的 `InterfaceInfoController`，在最下面添加这个接口：

```java
    @Resource
    private ApiClient apiClient;
   
   /**
     * 在线调用（测试）接口
     */
    @PostMapping("/invoke")
    @Operation(summary = "在线调用测试接口")
    public BaseResponse<Object> invokeInterfaceInfo(@RequestBody InterfaceInfoInvokeRequest invokeRequest){
//        1.校验参数
        if(invokeRequest == null || invokeRequest.getId() <=0){
            return ResultUtils.success(ErrorCode.PARAMS_ERROR);
        }

//        2.判断接口是否存在
        long id = invokeRequest.getId();
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if(oldInterfaceInfo == null){
            return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR,"接口不存在");
        }

//        3.判断接口状态是否开启（1是开启）
        if(oldInterfaceInfo.getStatus() != 1){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"接口已关闭");
        }

//        4.发起实际调用
//        这里应该根据oldInterfaceInfo.getUrl()动态去调
//        但是目前为了跑通主流程，先用if-else写死判断，只测试"/name/user"接口
        String userRequestParams = invokeRequest.getUserRequestParams();
        if(oldInterfaceInfo.getUrl().contains("/name/user")){
//            利用Hutool将前端传来的JSON字符串反序列化为User对象
        com.accycx.apiclientsdk.model.User user = cn.hutool.json.JSONUtil.toBean(userRequestParams, com.accycx.apiclientsdk.model.User.class);

//        主后台使用装配好的SDK客户端发起真实网络请求
            String result = apiClient.getUserNameByPost(user);
            return ResultUtils.success(result);
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR,"目前仅支持测试/name/user接口");
    }

```

写完这段代码后，`/invoke` 接口就完成了，现在我们可以把interface模块的接口地址信息用之前写过的增加接口功能存入数据库里，然后再用`ApiFox`测试这个新添的接口。

## 跨服务鉴权与 RPC 调用

之前我们为了跑通流程，在interface模块的拦截器里写了：`private static final String MOCK_SK = "accycx_test_sk1";`这样的模拟数据，并没有从数据库里查询真实数据。

**目前的问题：**

1. `interface` 模块收到了调用者的 `accessKey`。
2. 它需要查出对应的 `secretKey` 来验签。
3. 但用户信息存在主库 `api_platform` 中，**而 `interface` 作为一个模拟的第三方独立微服务，绝对不能直接连主库**（如果每个微服务都能直连主库，一旦数据库崩溃，全盘皆输，这就违背了微服务架构的初衷）。

所以我们需要用到**RPC（远程过程调用）** 

让 `interface` 模块在代码里打个“内线电话”，调用主业务 `backend` 模块里的方法去查数据库。这个“电话线”，我们要用到的技术选型就是 **Apache Dubbo**。

为了打通 Dubbo，我们分为**三步走**，现在直接开始第一步：**建立通信契约**。

### 第一步：在 Common 模块定义“内线服务接口”

在微服务中，两个服务要打电话，必须要有一个双方都认识的“电话簿”（公共接口）。这个电话簿理所当然要放在大家都能引用的 **`api-platform-common`** 模块里。

打开 `api-platform-common` 模块，在 `src/main/java/com/accycx/common` 下新建一个包 `service`，然后创建两个**专门用于内部调用的 RPC 接口**（注意：这和我们之前在 backend 里写的对外 Service 是不一样的）：

**1. 内部用户服务 (`InnerUserService.java`)** 

用于 `interface` 模块向 `backend` 查询用户的 AK/SK 是否合法。

```java
/**
 * 内部用户服务（仅供微服务内部调用）
 */
public interface InnerUserService {

    /**
     * 数据库中查是否已分配给用户秘钥（根据accessKey 查找到对应的User，里面包含secretKey）
     *
     * @param accessKey 用户秘钥
     * @return 如果找不到返回null
     */
    User getInvokeUser(String accessKey);
}

```

**2. 内部接口信息服务 (`InnerInterfaceInfoService.java`)** 

用于 `interface` 模块查询用户正在调用的这个接口，在数据库里是否存在？是不是开启状态？

```java
/**
 * 内部接口信息服务
 */
public interface InnerInterfaceInfoService {

    /**
     * 从数据库中查询接口是否存在（请求路径、请求方法、状态为开启）
     *
     * @param path 请求路径
     * @param method 请求方法
     * @return 如果找不到返回null
     */
    InterfaceInfo getInterfaceinfo(String path, String method);
}

```

这两个接口里用到了 `User` 和 `InterfaceInfo` 实体类。因为现在这两个类还在 `model` 模块里，而 `common` 并没有引入 `model`，引用一下就好了。

契约定好了，接下来就需要把**`Dubbo` 和 `Nacos`（注册中心）** 引入到项目中，让 `backend` 把电话接起来，让 `interface` 把电话拨出去。

### 第一步：环境整备（依赖与中间件）

在写代码前，我们得先在本地启动 `**Nacos**`。它就像是微服务世界的“中枢站”，没有它，服务之间找不到彼此。

1.启动 `Nacos` 服务，默认端口 8848

下载 `Nacos` 2.x 并在本地启动。启动成功后，访问 `http://localhost:8848/nacos`，看到控制台界面就说明中间件准备好了。

2.在父工程/Common 引入依赖，统一版本管理

在主 `pom.xml` 中引入 Spring Cloud Alibaba 依赖。然后在 `backend` 和 `interface` 的 `pom.xml` 中分别加入：

- `dubbo-spring-boot-starter`
- `spring-cloud-starter-alibaba-nacos-discovery`

### 第二步：服务端（backend）接电话

我们要让 `backend` 模块把之前在 `common` 里定义的契约接口实现出来，并通过 Dubbo 广播出去。

1. **实现内部服务类**

在 `api-platform-backend` 的 `service.impl` 下新建 `InnerUserServiceImpl.java`：

```java
@DubboService //核心：告诉Dubbo这是一个服务实现类，提供给其他微服务调用
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User getInvokeUser(String accessKey){
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("access_key", accessKey);
        return userMapper.selectOne(queryWrapper);
    }
}

```

2. **配置 backend 的 `application.yml`**

```yaml
dubbo:
  application:
    name: api-platform-backend # 服务名
  protocol:
    name: dubbo
    port: -1 # 随机可用端口
  registry:
    address: nacos://localhost:8848 # 注册到 Nacos
```

### 第三步：调用端（interface）拨电话

现在我们要改掉那个写死的 `MOCK_SK`，让拦截器去实时查数据库。

1. **配置 interface 的 `application.yml**`

```yaml
dubbo:
  application:
    name: api-platform-interface
  registry:
    address: nacos://localhost:8848
```

2. **重构拦截器 `ApiAuthInterceptor.java`**

在这里，我们用 `@DubboReference`把远在另一个进程的对象调过来

```java
public class ApiAuthInterceptor implements HandlerInterceptor {

    @DubboReference // 核心：远程引用 backend 暴露的服务
    private InnerUserService innerUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String accessKey = request.getHeader("accessKey");
        
        // 1. 动态查库：不再是 MOCK，而是真的去 backend 查
        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if (invokeUser == null) {
            throw new RuntimeException("无权限：AccessKey 错误");
        }
        
        // 2. 拿到真实的 secretKey 进行验签
        String secretKey = invokeUser.getSecretKey();
        String serverSign = SignUtils.genSign(body, secretKey);
        
        if (!sign.equals(serverSign)) {
            throw new RuntimeException("无权限：签名校验失败");
        }
        return true;
    }
}
```

最后记得在主类加上`@EnableDubbo`注解

```java
@SpringBootApplication
@EnableDubbo
public class ApiPlatformBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiPlatformBackendApplication.class, args);
	}

}

```

然后现在准备联调测试

**1.启动 `Nacos`**。

**2.启动 Backend**：查看 `Nacos` 控制台的“服务列表”，如果出现了 `api-platform-backend`，说明电话接通了。

如图：

![nacos列表显示](https://bu.dusays.com/2026/04/15/69df7eadb5bc1.png)

**3.启动 Interface**。

**4.运行之前的 SDK 测试用例**：如果控制台依然返回成功，说明这一通“跨服务内线电话”打通了！

结果如图：

![测试结果](https://bu.dusays.com/2026/04/12/69dbb4e6e7e31.png)

到这里本项目的阶段三就完成了，接下来将进入阶段四：网关与中间件（高并发攻坚），然后会先搭建统一API网关（Gateway）

**目前架构的痛点：** 现在我们的验签拦截器 (`ApiAuthInterceptor`) 是写在 `api-platform-interface` 这个具体的接口服务里的。 假设以后平台做大了，又开发了 `weather-interface`（天气服务）、`sms-interface`（短信服务）、`ai-interface`（AI 问答服务），难道要把这坨又臭又长的验签拦截器，在每一个项目里都复制粘贴一遍吗？如果哪天签名算法要升级，那得改多少个项目？

所以我们需要在所有微服务的最前面，建立一个统一的“海关大楼”（网关模块）。

1. 所有的第三方开发者（SDK）不再直接请求具体的接口，而是**把请求全部打到网关**。
2. **网关负责统一验签**：把拦截器里的代码搬到网关里，验签通过后，网关再负责把请求**路由（转发）**到对应的真实接口服务。
3. 保护真实服务：真实的接口服务将不再对外暴露端口，只允许网关访问。

另外还能解决接口调用次数统计的问题，因为我们做的是一个API开放平台，平台是要算计配额的。“调用次数”是这个项目的核心业务数据。网关不仅要验签，还要在转发请求成功后，**让这个用户的接口剩余调用次数减 1**。

这就需要我们用到之前建好的 `user_interface_info`（用户接口关系表），并且再次用到 Dubbo 的 RPC 调用。

接下来的路线：

**1.新建 网关模块** (`api-platform-gateway`)。

**2.迁移 鉴权逻辑**：把 `interface` 里的拦截器废弃，在网关里写一个 **全局过滤器 (`GlobalFilter`)** 来接管验证。

**3.实现 次数统计**：利用 Dubbo 发起 RPC 调用，在数据库里对调用次数进行 `count + 1`，剩余配额 `leftNum - 1`。

**4.路由 转发**：网关验证无误后，把请求平滑地送到 `interface`。

# 阶段四：网关与中间件（高并发攻坚）

本章是阶段四的上半部分，主要包括网关的搭建以及前面一些遗留下来的业务逻辑需要处理，还包括前端页面的展示。

# 网关（Gateway）

**Spring Cloud Gateway 的底层使用的是响应式编程（`WebFlux` + `Netty`），而不是我们之前一直用的 Spring MVC（Tomcat）。** 这意味着，在网关模块里，**绝对不能引入 `spring-boot-starter-web` 依赖**，否则项目启动会直接报错冲突！

## 配置网关类

1. `pom.xml` (网关依赖)

```xml
<dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>

        <dependency>
            <groupId>com.accycx</groupId>
            <artifactId>api-platform-common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
```

### 2. `application.yml`

用来配置网关的端口、`Nacos` 注册以及核心的**路由转发规则**。

```yaml
server:
  port: 8090 # 网关占用独立的 8090 端口

spring:
  application:
    name: api-platform-gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      routes:
        - id: api_route # 路由的 ID，随便起，保持唯一即可
          # 当有人访问网关的 /api/** 路径时，网关会自动把请求转发给真实的 interface 服务 (8102)
          uri: http://localhost:8102
          predicates:
            - Path=/api/**
          filters:
            - StripPrefix=1 # 去掉路径中的 /api 前缀，转发给后端服务
dubbo:
  application:
    name: api-platform-gateway
  registry:
    address: nacos://localhost:8848

```

### 3. 创建网关启动类 

在 `src/main/java/com/accycx/gateway` 下新建 `ApiGatewayApplication.java`：

```java
//网关不需要连接数据库，所以排除数据库自动配置，防止报错
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDubbo
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

```

4. `CustomGlobalFilter.java` (全局鉴权拦截器)

在 `filter` 包下新建这个类：

```java
@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

// 远程调用主后台获取用户信息
    @DubboReference(check = false)
    private InnerUserService innerUserService;

//Mono<Void> 是 Reactor 框架中的一个类型，表示一个异步操作的结果，这个操作可能会完成（成功或失败），但不会返回任何数据（Void）。
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

// 1. 打印请求日志
        log.info("请求唯一标识：" + request.getId());
        
//request.getPath() 返回的是一个 RequestPath 对象。这个对象里包含了很多解析好的路径信息，比如按 / 分割的各个部分。如果直接打印它，底层会调用 toString()。
//request.getPath().value() 则是直接把完整的路径提取成一个纯粹的 String 字符串（比如 "/api/user/login"）。在写日志时，我们通常只需要纯字符串。
        log.info("请求路径：" + request.getPath().value());
        
//request.getLocalAddress() 返回的是一个 InetSocketAddress 对象，里面包含了 IP 地址、端口号，甚至还有未解析的主机名。
//request.getLocalAddress().getHostString() 则是干净利落地只提取出 IP 地址字符串（比如 "192.168.1.100"），且它不会触发耗时的 DNS 反向解析，性能更好，写进日志也更清晰。
        log.info("请求来源地址：" + request.getLocalAddress().getHostString());

        // 2. 扒取请求头（核心鉴权部分）
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String sign = headers.getFirst("sign");
        String body = headers.getFirst("body"); 

        // 3. 校验逻辑 (防伪造、防重放、防过期)
        if (accessKey == null) {
            return handleNoAuth(response);
        }

        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if (invokeUser == null) {
            return handleNoAuth(response);
        }

        if (nonce == null || nonce.length() < 4) {
            return handleNoAuth(response);
        }

        long currentTime = System.currentTimeMillis() / 1000;
        final long FIVE_MINUTES = 5 * 60;
        if (timestamp == null || (currentTime - Long.parseLong(timestamp)) >= FIVE_MINUTES) {
            return handleNoAuth(response);
        }

        String secretKey = invokeUser.getSecretKey();
        String serverSign = SignUtils.genSign(body, secretKey);
        if (sign == null || !sign.equals(serverSign)) {
            return handleNoAuth(response);
        }

        // 4. 鉴权通过，放行请求！
        return chain.filter(exchange);
    }

    /**
     * 拦截并返回 403 无权限错误
     */
    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        // 返回 -1 保证这个过滤器拥有最高优先级，最先执行
        return -1;
    }
}
```

网关配置好之后，**必须去 `interface` 模块，把之前写的 `ApiAuthInterceptor`（拦截器）和 `MvcConfig` 删掉** 如果网关拦截一次，接口服务又拦截一次，会导致请求卡死或抛出异常。

## 简单测试

### 测试网关的“拦截”

现在全面启动项目，测试网关的拦截能力，按照下面顺序启动：

**1.Nacos (注册中心)**：确保 `localhost:8848/nacos` 正在运行。

**2.API 平台主后台 (Backend)**：点击 `api-platform-backend` 模块的启动类。确保它已成功注册到 `Nacos`。

**3.模拟的第三方接口 (Interface)**：点击 `api-platform-interface` 模块的启动类（注意：它现在是一个没有任何安检的“裸体”服务，跑在 `8102` 端口）。

**4.统一 API 网关 (Gateway)**：点击我们刚刚新建的 `api-platform-gateway` 模块的启动类 `ApiGatewayApplication`（它跑在 `8090` 端口）。

然后现在可以直接访问网关的地址，并且故意不带任何签名信息，因为我们配置的路由规则是匹配`/api/**`，所以要这样访问：

http://localhost:8090/api/name/get?name=Test

预期结果：页面会返回一个**403 Forbidden** 状态码，并且看看 Gateway 的控制台日志，会显示打印的请求信息，并在验证头信息（`accessKey == null`）时被果断拦截了。

### 测试网关的“放行与路由”

这是最核心的一步：通过 SDK，带着合法的签名，向网关发起请求，看网关能不能把它正确地路由给隐藏在后面的 `8102` 服务。

**注意!** 之前我们测试 SDK，请求的地址是直接写死的接口地址（`8102`）。现在我们要通过网关，所以请求地址必须改成网关的地址（`8090`）加上路由前缀（`/api`）。

打开之前写的 **SDK 模块 (`api-platform-client-sdk`)**，找到 `ApiClient.java`。

把里面请求的 URL 地址**全部替换**为通过网关的地址：

```java
// 1. 调用 GET 接口
    public String getNameByGet(String name) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);
        // 注意：这里改成了访问网关 (8090)，并加上了 /api 前缀
        String result = HttpUtil.get("http://localhost:8090/api/name/get", paramMap);
        System.out.println(result);
        return result;
    }

    // 2. 调用 POST URL 传参接口
    public String getNameByPost(String name) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);
        // 注意：访问网关
        String result = HttpUtil.post("http://localhost:8090/api/name/post", paramMap);
        System.out.println(result);
        return result;
    }

    // 3. 调用最核心的 POST JSON 接口
    public String getUserNameByPost(User user) {
        String json = JSONUtil.toJsonStr(user);
        // 注意：访问网关
        HttpResponse httpResponse = HttpRequest.post("http://localhost:8090/api/name/user")
                .addHeaders(getHeaderMap(json))
                .body(json)
                .execute();
        System.out.println(httpResponse.getStatus());
        String result = httpResponse.body();
        System.out.println(result);
        return result;
    }
```

**注意：修改完 SDK 后，必须要在 Maven 面板里重新双击 `clean` 和 `install`，把这个带有新地址的 jar 包安装到本地仓库！** 否则接下来的测试还是会去调老地址。

重新打包好 SDK 后，打开 `api-platform-interface` 模块下的测试类 `ApiInterfaceApplicationTests.java`，会返回期望的结果：

![测试结果](https://bu.dusays.com/2026/04/12/69dbb4e6e7e31.png)

网关模块后台会打印日志：

![日志](https://bu.dusays.com/2026/04/15/69df82ab9366a.png)

到这里，这个复杂的跨进程链路就彻底跑通了：

SDK（客户端）发起请求 -> 打向 Gateway（8090） -> Gateway 进行签名拦截 -> Gateway 通过 Dubbo RPC 去 Backend 查数据库比对签名 -> 签名一致，Gateway 放行 -> Gateway 根据路由规则，将请求转发给 Interface（8102） -> Interface 执行业务逻辑并原路返回

## 接口调用次数统计

我们要实现这样的业务闭环：当用户成功调用一次 `/name/user` 接口后，系统会自动在数据库里扣除他该接口的 1 次调用配额，并把总调用次数加 1。

**架构思考：这段代码应该写在哪里？**

1. **写在 `Interface` 模块？** 不行，第三方接口服务只负责干活（比如返回天气），它不应该、也没有权限去管“平台扣费”这种核心业务。
2. **写在网关 `Gateway` 模块？** 也不行，网关应该尽可能轻量，只做路由和拦截。如果在网关里写长篇大论的数据库操作，会严重拖慢网关的并发性能。
3. **正确答案：写在 `Backend` 模块，由网关通过 RPC 异步调用**

接下来，我们要实现这一功能。

### 第一步：在 `Common` 模块建立契约 (RPC 接口)

要在网关和 `Backend` 之间打通一条新的“内线电话”，用于统计次数。

打开 **`api-platform-common`** 模块的 `src/main/java/com/accycx/common/service` 目录，新建 `InnerUserInterfaceInvoke.java`：

```java
/**
 * 内部用户接口信息服务（专门用于网关RPC调用）
 */
public interface InnerUserInterfaceInvoke {

    /**
     * 接口调用次数+1，剩余配额-1
     *
     * @param interfaceInfoId 被调用的接口ID
     * @param userId          发起调用的用户ID
     * @return 是否统计成功
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}

```

### 第二步：在 `Backend` 模块实现契约并暴露服务

打开 **`api-platform-backend`** 模块的 `src/main/java/com/accycx/backend/service/impl` 目录，新建 `InnerUserInterfaceInvokeImpl.java`：

```java
@DubboService
@SuppressWarnings("unused")
public class InnerUserInterfaceInvokeImpl implements InnerUserInterfaceInvoke {

    @Resource
    private UserInterfaceInvokeService userInterfaceInvokeService;

    @Override
    public boolean invokeCount(long interfaceInfoId,long userId){
//       检验参数合法性
        if(interfaceInfoId <= 0 || userId <= 0){
            return false;
        }

//        用UserInterfaceInvoke实体类更新

        UpdateWrapper<UserInterfaceInvoke> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("interface_info_id",interfaceInfoId);
        updateWrapper.eq("user_id",userId);
        updateWrapper.gt("left_num",0); //必须有剩余次数点才能扣除

//         子操作，防并发超卖
        updateWrapper.setSql("left_num = left_num - 1, total_num = total_num + 1");

        return userInterfaceInvokeService.update(updateWrapper);
    }
}

```

这里用到了`UserInterfaceInvokeService`，但是之前并没有写过，只创建了`UserInterfaceInvoke`的entity，这一套（`Mapper`、`Service`、`ServiceImpl`）将在下一步实现，并把代码贴出来。

### 第三步：在 `Gateway` 网关模块发起 RPC 调用

这是最关键的一步。我们不能在网关**转发请求之前**扣费，万一接口服务挂了没返回结果，用户钱白扣了。必须在**网关收到接口服务的成功响应后**再扣费。

在实现这步之前，先把`UserInterfaceInvoke`这一套流程创建好

#### 完成`UserInterfaceInvoke`流程

**（1）新建 Mapper接口**

在 `api-platform-backend` 模块的 `mapper` 包下新建 `UserInterfaceInvokeMapper.java`：

```java
/**
 * 用户接口调用表 Mapper接口
 */
@Mapper
public interface UserInterfaceInvokeMapper extends BaseMapper<UserInterfaceInvoke> {
}

```

**（2）新建 Service 接口**

在 `service` 包下新建 `UserInterfaceInvokeService.java`：

```java
/**
 * 用户接口调用服务接口
 */
public interface UserInterfaceInvokeService extends IService<UserInterfaceInvoke> {
}

```

**（3）实现 Service 接口**

在 `service/impl` 包下新建 `UserInterfaceInvokeServiceImpl.java`：

```java
/**
 * 用户接口调用服务实现类
 */
@Service
public class UserInterfaceInvokeServiceImpl extends ServiceImpl<UserInterfaceInvokeMapper,UserInterfaceInvoke> implements UserInterfaceInvokeService{
}

```

为什么要把 `UserInterfaceInvokeServiceImpl`和`InnerUserInterfaceInvokeImpl`分开来写？

有人会觉得，就在`UserInterfaceInvokeServiceImpl`类上同时加上`@DubboService`和`@Service`就行了，这样确实能跑通，但是同时也会带来很大的风险！

加上`@DubboService`意味着把这个业务类的接口全都暴露给其它微服务模块了，也就相当于这个业务类继承的`Mybat-Plus`里的底层接口也会暴露出去，例如`save()`,`update()`等方法，如果微服务其它模块被黑客入侵，那么他就可以直接用这些底层接口方法直接去操作你的数据库，及其不安全，所以要分开来写，只把必要的接口暴露出去。

接下来就在`Gateway`网关模块发起RPC调用

打开 **`api-platform-gateway`** 模块里的 `CustomGlobalFilter.java`

1. **在类顶部引入刚刚写的 RPC 接口：**

```java
@DubboReference(check = false)
    private InnerUserInterfaceInfoService innerUserInterfaceInfoService;
    
    // 之前引入的 innerUserService 应该也在这里
```

2. **找到 `// 4. 鉴权通过，放行请求！` 这行代码，把它替换成下面的响应式处理逻辑：**

```java
// 4. 鉴权通过，放行请求，并绑定一个“响应后置拦截器”
            // 注意：不要直接 return chain.filter(exchange); 了
            
            // 假设我们已经查到了正在被调用的 interfaceInfoId（比如 ID 是 1）
            // 这里需要通过 innerInterfaceInfoService 从数据库里动态查出来，等会儿会实现这一步
            long interfaceInfoId = 1L; 
            
            // 发起异步的网关转发并处理结果
            return handleResponse(exchange, chain, interfaceInfoId, invokeUser.getId());
```

3. **在 `CustomGlobalFilter` 类的最下方，加入这个处理响应的方法：**

```java
/**
     * 处理响应，在响应成功后调用统计次数的RPC接口
     */
    private Mono<Void> handleResponse(ServerWebExchange exchange,GatewayFilterChain chain,long interfaceInfoId,long userId){
        try{
            ServerHttpResponse originalResponse = exchange.getResponse();

//            这是一段Gateway装饰器模式代码，用于拦截后端服务的响应
//            这里不修改响应内容，只在响应完成后执行一个回调函数（Mono.fromRunnable），在这个回调函数里可以获取到响应的状态码等信息，进行统计或日志记录等操作
            return chain.filter(exchange).then(Mono.fromRunnable(()->{
                HttpStatusCode statusCode = originalResponse.getStatusCode();
                if(statusCode != null && statusCode.is2xxSuccessful()){
//                    如果后端接口返回了200，说明调用成功了，然后可以调用RPC接口来统计用户的调用次数
                    try {
                        // 1. 接住返回值
                        boolean invokeResult = innerUserInterfaceInvoke.invokeCount(interfaceInfoId, userId);

                        // 2. 判断是否扣减成功
                        if (!invokeResult) {
                            log.error("致命错误：接口调用成功，但扣减调用次数失败！接口ID: {}, 用户ID: {}", interfaceInfoId, userId);
                            // 这里可以配合未来的报警系统（比如发钉钉/企业微信机器人报警）
                        } else {
                            log.info("扣减调用次数成功。接口ID: {}, 用户ID: {}", interfaceInfoId, userId);
                        }
                    } catch (Exception e) {
                        log.error("invokeCount RPC调用出现异常", e);
                    }
                }else{
//                    接口报错了，做一些报警或日志记录
                    log.error("调用接口失败，状态码：{}", statusCode);
                }
            }));
        }catch(Exception e){
            log.error("网关处理异常",e);
            return exchange.getResponse().setComplete();
        }
    }
}
```

这段代码其实在同步和异步上有问题：

网关基于 `WebFlux`，它的核心思想是**非阻塞**。`chain.filter(exchange).then(Mono.fromRunnable(...))` 这个回调函数是运行在 `Netty` 的非阻塞线程（`EventLoop`）中的。 而 `innerUserInterfaceInvoke.invokeCount(...)` 是一个**同步阻塞的 Dubbo RPC 调用**。

如果我们在非阻塞的线程里执行一个可能耗时几百毫秒的同步网络请求，会导致网关的线程被卡住，吞吐量断崖式下跌，甚至直接抛出 `block() is not allowed` 类似的异常。

并发问题：

假设用户小明**只剩下最后 1 次**调用额度（`leftNum = 1`），他写了一个并发脚本，**在同一微秒内，向网关发起了 2 个完全一样的请求（请求 A 和请求 B）**。

在极高并发下，A 和 B 几乎是同时刻并排冲进网关的：

- **步骤 1：A 和 B 同时到达网关安检。** 网关同时去查数据库，发现小明剩余次数都是 1，于是**同时放行了 A 和 B**。（因为此时没有任何一个请求走到了最终扣费那一步）。
- **步骤 2：A 和 B 同时调用第三方接口。** 两个接口都调用成功了（因为第三方不管你剩多少次，它只负责干活）。
- **步骤 3：A 和 B 同时走到 `invokeCount` 准备扣费。**
  - 请求 A 执行 `updateWrapper.gt("leftNum", 0)`，发现当前是 1，符合条件，扣减成功！小明次数变成 0。返回 `true`。
  - **紧接着，请求 B 也来执行 `updateWrapper.gt("leftNum", 0)`。** 但此时数据库里的 `leftNum` 刚刚已经被 A 变成了 0。因为条件不成立（0 不大于 0），`MyBatis-Plus` 没有更新任何数据，**返回 `false`**。

结果是：第三方接口被调用了 **2 次**，但小明的账户里只被扣了 **1 次**，这就是典型的“白嫖”！！！

所以后续这部分会用Redis来处理

### 在网关模块动态查询接口ID

#### 第一步：在 Backend 模块实现 RPC 契约

之前我们在 `common` 模块定义了 `InnerInterfaceInfoService` 接口，现在我们要去 **`api-platform-backend`** 模块把它给实现了。

在 `service/impl` 包下新建 `InnerInterfaceInfoServiceImpl.java`：

```java
@DubboService
@SuppressWarnings("unused")
public class InnerInterfaceInfoServiceImpl implements InnerInterfaceInfoService {

    @Resource
    private InterfaceInfoMapper interfaceInfoMapper;

    @Override
    public InterfaceInfo getInterfaceInfo(String path,String method){
        if(StringUtils.isAnyBlank(path,method)){
            return null;
        }
//        根据URL和请求方法 唯一确定一个接口
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("url",path);
        queryWrapper.eq("method",method);
        return interfaceInfoMapper.selectOne(queryWrapper);
    }

}

```

#### 第二步：在 Gateway 中实现动态查询

现在我们要重构 **`api-platform-gateway`** 里的 `CustomGlobalFilter`。我们需要把原来写死的 `1L` 替换成从数据库查询的结果。

**1. 注入 RPC 服务**

在 `CustomGlobalFilter` 类顶部新增：

```java
    @DubboReference(check = false)
    @SuppressWarnings("unused")
    private InnerInterfaceInfoService innerInterfaceInfoService;
```

### 2. 修改 filter 核心逻辑

找到之前 `// 4. 鉴权通过` 的位置，修改如下：

```java

//        4.鉴权通过，放行请求，并绑定一个回调函数，在响应成功后调用统计次数的RPC接口
//          动态获取当前请求的接口信息
        String path = request.getPath().value().replace("/api",""); //去掉/api前缀，得到真正的接口路径
        String method = request.getMethod().name();

//        这里调用RPC查库
        InterfaceInfo interfaceInfo = innerInterfaceInfoService.getInterfaceInfo(path, method);
        if(interfaceInfo == null){
//            找不到接口，说明这是非法路径或未收录接口
            return handleNoAuth(response);
        }

//        拿到真实ID和用户ID
        long interfaceInfoId = interfaceInfo.getId();
        long userId = invokeUser.getId();

//        发起异步的网关转发并处理结果
        return handleResponse(exchange, chain, interfaceInfoId, userId);
//        return chain.filter(exchange);

```

现在的闭环链路：

1. **SDK** 向网关发请求。
2. **网关** 通过 `InnerUserService` 拿到 **SK** 完成验签。
3. **网关** 通过 `InnerInterfaceInfoService` 拿到该请求对应的 **接口 ID**。
4. **网关** 转发给真实服务。
5. **网关** 收到成功响应，通过 `InnerUserInterfaceInfoService` 给该 **用户ID + 接口ID** 的组合扣费。

但是：现在的网关每进一个请求都要打两次 RPC 到后台查库（一次查人，一次查接口），这在并发量大的时候会拖慢响应速度，后面会通过Redis缓存优化网关的查询性能。

现在重新启动整个项目，再用之前的测试类验证流程（注意，在测试前数据库需要用一条用户和接口的调用的关系的数据，并且用户id要对应测试类里的用户名，接口id对应测试类里的接口，调用配额要为有效数字）。

这次通过`Gateway`后台可以看到：

![日志打结果](https://bu.dusays.com/2026/04/15/69df906c41d6a.png)

然后去观察数据库里的变化，会发现总调用次数+1，剩余配额-1。

到现在这个项目的核心计费与鉴权大动脉已经彻底贯通，这标志着后端的核心架构（接口提供、SDK 封装、微服务治理、网关统一拦截、RPC 通信计费）已经基本成型。

现在我们要去写前端页面，让用户有一个真正的页面去：

1. 浏览所有的接口列表。
2. 注册账号并查看自己的 AK/SK。
3. 在网页上点击“申请调用”，分配调用次数。
4. 在网页上填参数，一键实现在线测试（也就是去触发我们之前在 `backend` 里写的那个 `/invoke` 接口）。

在写前端的同时，还可以根据前端页面需要展示的东西去完善之前写过的接口，或者添加新的需要的接口。

等前后端彻底打通后，就开始对项目进行性能优化，引入中间件解决一系列问题。
