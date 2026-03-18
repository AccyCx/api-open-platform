package com.accycx.model.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

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
