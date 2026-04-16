package com.accycx.common.service;
/**
 * 内部用户接口信息服务（专门用于网关RPC调用）
 */
public interface InnerUserInterfaceInvoke {

    /**
     * 接口调用次数+1，剩余配额-1
     *
     * @param interfaceInfoId 被调用的接口ID
     * @param userId          发起调用的用户ID
     * @return 是否统计成功
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}

