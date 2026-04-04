package com.accycx.model.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.io.StringReader;
import java.util.Date;

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
    private Integer isDelete;

    @TableField(exist = false) //这个字段在数据库表里不存在，不参与ORM映射
    private static final long serialVersionUID = 1L; //序列化版本号

}
