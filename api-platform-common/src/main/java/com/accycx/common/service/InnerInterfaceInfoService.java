package com.accycx.common.service;


import com.accycx.model.entity.InterfaceInfo;

/**
 * 内部接口信息服务
 */
public interface InnerInterfaceInfoService {

    /**
     * 从数据库中查询接口是否存在（请求路径、请求方法、状态为开启）
     *
     * @param path 请求路径
     * @param method 请求方法
     * @return 如果找不到返回null
     */
    InterfaceInfo getInterfaceinfo(String path, String method);
}
