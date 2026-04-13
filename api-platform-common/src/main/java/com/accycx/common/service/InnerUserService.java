package com.accycx.common.service;
import com.accycx.model.entity.User;

/**
 * 内部用户服务（仅供微服务内部调用）
 */
public interface InnerUserService {

    /**
     * 数据库中查是否已分配给用户秘钥（根据accessKey 查找到对应的User，里面包含secretKey）
     *
     * @param accessKey 用户秘钥
     * @return 如果找不到返回null
     */
    User getInvokeUser(String accessKey);
}
