package com.accycx.backend.service.impl;


import com.accycx.backend.mapper.InterfaceInfoMapper;
import com.accycx.common.service.InnerInterfaceInfoService;
import com.accycx.model.entity.InterfaceInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@SuppressWarnings("unused")
public class InnerInterfaceInfoServiceImpl implements InnerInterfaceInfoService {

    @Resource
    private InterfaceInfoMapper interfaceInfoMapper;

    @Override
    public InterfaceInfo getInterfaceInfo(String path,String method){
        if(StringUtils.isAnyBlank(path,method)){
            return null;
        }
//        根据URL和请求方法 唯一确定一个接口
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("url",path);
        queryWrapper.eq("method",method);
        return interfaceInfoMapper.selectOne(queryWrapper);
    }

}
