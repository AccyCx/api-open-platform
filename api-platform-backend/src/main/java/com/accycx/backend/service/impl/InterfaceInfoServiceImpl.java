package com.accycx.backend.service.impl;
import com.accycx.backend.mapper.InterfaceInfoMapper;
import com.accycx.backend.service.InterfaceInfoService;
import com.accycx.model.entity.InterfaceInfo;
import com.accycx.model.vo.interfaces.InterfaceInfoStatisticsVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;



/**
 * 接口信息服务实现类
 */
@Service
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo> implements InterfaceInfoService {

    @Override
    public void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add){

//        1.校验参数是否为空
        if(interfaceInfo == null){
            throw new RuntimeException("接口信息不能为空");
        }

//        2.接收参数
        String name = interfaceInfo.getName();
        String url = interfaceInfo.getUrl();
        String method = interfaceInfo.getMethod();

//        如果是新增操作（add为true），所有必填参数不能为空
        if(add){
            if(StringUtils.isAnyBlank(name,url,method)){
                throw new RuntimeException("接口名称、URL和请求方法不能为空");
            }
        }

//        无论是新增还是修改，都要校验业务规则（比如名字不能太长）
        if(StringUtils.isNotBlank(name) && name.length()>50){
            throw new RuntimeException("接口名称过长");
        }

    }

    @Override
    public InterfaceInfoStatisticsVO getInterfaceStatistics() {
        InterfaceInfoStatisticsVO vo = new InterfaceInfoStatisticsVO();

        // 1. 查询总数
        long totalNum = this.count();

        // 2. 查询已发布数 (假设 status = 1 为上线)
        long onlineNum = this.count(new QueryWrapper<InterfaceInfo>().eq("status", 1));

        // 3. 查询下线数 (假设 status = 0 为下线)
        long offlineNum = this.count(new QueryWrapper<InterfaceInfo>().eq("status", 0));

        vo.setTotalNum(totalNum);
        vo.setOnlineNum(onlineNum);
        vo.setOfflineNum(offlineNum);

        return vo;
    }
}
