package com.accycx.common;

import com.accycx.common.enums.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用返回类
 *
 * @param <T> 返回的数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code; //状态码
    private T data; //返回的数据
    private String message; //返回的信息

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode){
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

}
