package com.accycx.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

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
