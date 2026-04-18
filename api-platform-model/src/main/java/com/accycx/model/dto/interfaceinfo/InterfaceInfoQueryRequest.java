package com.accycx.model.dto.interfaceinfo;


import com.accycx.model.dto.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 接口信息查询请求体
 */
@Data
@EqualsAndHashCode(callSuper = true) //让 Lombok 在自动生成 equals() 和 hashCode() 方法时，把父类（也就是 PageRequest）里的属性也一起算进去
public class InterfaceInfoQueryRequest extends PageRequest implements Serializable {

//    接口名称（支持模糊搜索）
    private String name;

//    接口描述（支持模糊搜索）
    private String description;

//    接口请求方法
    private String method;

//    接口状态（0-关闭，1-开启）
    private Integer status;

    @Serial
    private static final long serialVersionUID = 1L;
}
