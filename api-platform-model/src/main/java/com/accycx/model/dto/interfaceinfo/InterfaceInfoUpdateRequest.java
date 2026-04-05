package com.accycx.model.dto.interfaceinfo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新接口请求体
 */
@Data
public class InterfaceInfoUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L; //序列化版本号

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
}
