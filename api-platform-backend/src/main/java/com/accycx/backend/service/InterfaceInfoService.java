package com.accycx.backend.service;

import com.accycx.model.entity.InterfaceInfo;
import com.accycx.model.vo.interfaces.InterfaceInfoStatisticsVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 接口信息服务接口
 */
public interface InterfaceInfoService extends IService<InterfaceInfo> {

//       复杂的校验逻辑方法
        void validInterfaceInfo(InterfaceInfo interfaceInfo,boolean add);


        /**
         * 获取接口统计数据
         *
         * @return 包含总数、上线数、下线数的统计对象
         */
        InterfaceInfoStatisticsVO getInterfaceStatistics();
}
