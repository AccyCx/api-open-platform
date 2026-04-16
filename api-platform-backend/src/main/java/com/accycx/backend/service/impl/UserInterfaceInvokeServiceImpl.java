package com.accycx.backend.service.impl;

import com.accycx.backend.mapper.UserInterfaceInvokeMapper;
import com.accycx.backend.service.UserInterfaceInvokeService;
import com.accycx.model.entity.UserInterfaceInvoke;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户接口调用服务实现类
 */
@Service
public class UserInterfaceInvokeServiceImpl extends ServiceImpl<UserInterfaceInvokeMapper,UserInterfaceInvoke> implements UserInterfaceInvokeService{
}
