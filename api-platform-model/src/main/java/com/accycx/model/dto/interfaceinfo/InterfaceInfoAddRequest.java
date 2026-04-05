package com.accycx.model.dto.interfaceinfo;


import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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
