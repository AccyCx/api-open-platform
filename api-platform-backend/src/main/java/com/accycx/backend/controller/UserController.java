package com.accycx.backend.controller;

import com.accycx.backend.service.UserService;
import com.accycx.common.BaseResponse;
import com.accycx.common.ErrorCode;
import com.accycx.common.utils.ResultUtils;
import com.accycx.model.dto.user.UserLoginRequest;
import com.accycx.model.dto.user.UserRegisterRequest;
import com.accycx.model.vo.user.LoginUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口
 */
@RestController  //标注这是一个 RESTful 控制器，返回 JSON 数据
@RequestMapping("/user") //接口基础路径
@Tag(name = "用户接口", description = "用户的注册、登录与管理")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     *
     * @param userRegisterRequest 封装了前端传来的账号、密码、确认密码
     * @return 统一返回格式，包含新注册用户的ID
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest){
//        1.校验请求体是否为空
        if(userRegisterRequest == null){
//            使用封装的统一错误码返回
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }

//        2.提取参数
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

//        3.Controller层做一层基础的非空校验（Service层做深度业务校验）
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "账号、密码或确认密码不能为空");
        }

//        4.调用Service层执行真正的注册落库逻辑
        long result = userService.userRegister(userAccount, userPassword, checkPassword);

//        5.将结果包装成标准格式返回给前端
        return ResultUtils.success(result);
    }

    /**
     * 用户登录接口
     *
     * @param userLoginRequest 封装了前端传来的账号和密码
     * return 统一返回格式，包含登录用户的基本信息和令牌
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest){

//        1.校验请求体是否为空
        if(userLoginRequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }

//        2.提取参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

//        3.Controller层做一层基础的非空校验（Service层做深度业务校验）
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }

//       获取包含Token的完整登录信息
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword);

        return ResultUtils.success(loginUserVO);
    }



}
