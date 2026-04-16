package com.accycx.backend.service.impl;

import com.accycx.backend.service.UserInterfaceInvokeService;
import com.accycx.common.service.InnerUserInterfaceInvoke;
import com.accycx.model.entity.UserInterfaceInvoke;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@SuppressWarnings("unused")
public class InnerUserInterfaceInvokeImpl implements InnerUserInterfaceInvoke {

    @Resource
    private UserInterfaceInvokeService userInterfaceInvokeService;

    @Override
    public boolean invokeCount(long interfaceInfoId,long userId){
//       检验参数合法性
        if(interfaceInfoId <= 0 || userId <= 0){
            return false;
        }

//        用UserInterfaceInvoke实体类更新

        UpdateWrapper<UserInterfaceInvoke> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("interface_info_id",interfaceInfoId);
        updateWrapper.eq("user_id",userId);
        updateWrapper.gt("left_num",0); //必须有剩余次数点才能扣除

//         子操作，防并发超卖
        updateWrapper.setSql("left_num = left_num - 1, total_num = total_num + 1");

        return userInterfaceInvokeService.update(updateWrapper);
    }
}
