package com.accycx.backend.service;

import com.accycx.model.entity.User;
import com.accycx.model.vo.user.LoginUserVO;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

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

    /**
     * 获取当前登录用户的信息
     *
     * @param request HTTP 请求对象
     * @return 当前登录的用户信息，如果没有登录则返回 null
     */
    User getLoginUser(HttpServletRequest request);

}


