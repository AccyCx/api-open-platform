package com.accycx.backend.service;

import com.accycx.model.entity.User;
import com.accycx.model.vo.user.LoginUserVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param checkPassword 确认密码
     * @return 新用户 id
     */
    long userRegister(String userAccount,String userPassword,String checkPassword);

        /**
        * 用户登录
        *
        * @param userAccount 用户账号
        * @param userPassword 用户密码
        * @return 登录成功的用户返回体
        */
        LoginUserVO userLogin(String userAccount, String userPassword);
}
