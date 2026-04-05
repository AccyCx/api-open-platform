package com.accycx.model.dto.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用删除请求体
 */
@Data
public class DeleteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L; //序列化版本号

    private Long id; //要删除的记录ID
}
