package com.accycx.backend.service.impl;

import com.accycx.backend.mapper.UserMapper;
import com.accycx.common.service.InnerUserService;
import com.accycx.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService //核心：告诉Dubbo这是一个服务实现类，提供给其他微服务调用
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User getInvokeUser(String accessKey){
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("access_key", accessKey);
        return userMapper.selectOne(queryWrapper);
    }
}
