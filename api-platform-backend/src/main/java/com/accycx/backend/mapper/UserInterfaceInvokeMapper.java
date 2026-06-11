package com.accycx.backend.mapper;

import com.accycx.model.entity.UserInterfaceInvoke;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户接口调用表 Mapper接口
 */
@Mapper
public interface UserInterfaceInvokeMapper extends BaseMapper<UserInterfaceInvoke> {

    /**
     * 查询调用次数最多的前 N 个接口
     *
     * @param limit 限制数量
     * @return 包含接口 ID 和总调用次数的列表
     */
    @Select("SELECT interface_info_id AS interfaceInfoId, SUM(total_num) AS totalNum " +
            "FROM user_interface_invoke " +
            "GROUP BY interface_info_id " +
            "ORDER BY totalNum DESC " +
            "LIMIT #{limit}")
    List<UserInterfaceInvoke> listTopInvokeInterfaceInfo(@Param("limit") int limit);

}
