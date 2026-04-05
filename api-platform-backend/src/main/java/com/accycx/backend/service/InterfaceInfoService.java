package com.accycx.backend.service;

import com.accycx.model.entity.InterfaceInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 接口信息服务接口
 */
public interface InterfaceInfoService extends IService<InterfaceInfo> {

//       复杂的校验逻辑方法
        void validInterfaceInfo(InterfaceInfo interfaceInfo,boolean add);
}
