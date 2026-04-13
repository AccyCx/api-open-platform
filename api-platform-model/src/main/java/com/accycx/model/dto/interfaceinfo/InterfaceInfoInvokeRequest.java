package com.accycx.model.dto.interfaceinfo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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
