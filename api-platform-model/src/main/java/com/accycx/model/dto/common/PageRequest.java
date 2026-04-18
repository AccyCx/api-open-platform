package com.accycx.model.dto.common;

import lombok.Data;

/**
 * 分页请求基类
 */
@Data
public class PageRequest {

//    当前页号
    private long current = 1;

//    页面大小
    private long pageSize = 10;

//    排序字段
    private String sortField;

//    排序方式（默认升序）
    private String sortOrder = "ascend";
}
