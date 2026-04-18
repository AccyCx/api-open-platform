package com.accycx.common.utils;

import com.accycx.common.BaseResponse;
import com.accycx.common.enums.ErrorCode;

/**
 * 返回工具类
 */
public class ResultUtils {

    //    成功
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    //    失败
    public static <T> BaseResponse<T> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

    //    失败（自定义状态码和信息）
    public static <T> BaseResponse<T> error(int code,String message){
        return new BaseResponse<>(code,null,message);
    }

    //    失败（综合枚举和自定义信息）
    public static <T> BaseResponse<T> error(ErrorCode errorCode,String message){
        return new BaseResponse<>(errorCode.getCode(),null,message);
    }

}