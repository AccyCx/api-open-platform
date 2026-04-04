package com.accycx.backend.mapper;

import com.accycx.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * 用户表 Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
