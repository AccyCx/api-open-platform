package com.accycx.backend.controller;

import com.accycx.backend.service.UserService;
import com.accycx.common.AuthCheck;
import com.accycx.common.BaseResponse;
import com.accycx.common.enums.ErrorCode;
import com.accycx.common.utils.KeyUtils;
import com.accycx.common.utils.PasswordUtils;
import com.accycx.common.utils.ResultUtils;
import com.accycx.model.dto.user.UserAddrequest;
import com.accycx.model.dto.user.UserLoginRequest;
import com.accycx.model.dto.user.UserRegisterRequest;
import com.accycx.model.entity.User;
import com.accycx.model.vo.user.LoginUserVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口
 */
@Slf4j
@RestController  //标注这是一个 RESTFul 控制器，返回 JSON 数据
@RequestMapping("/user") //接口基础路径
@Tag(name = "用户接口", description = "用户的注册、登录与管理")
public class UserController {

    @Resource
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


        log.info("新用户注册成功，账号: {}, 分配的用户ID: {}", userAccount, result);
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

        log.info("用户登录成功，账号: {}, 角色: {}, 用户ID: {}", userAccount, loginUserVO.getUserRole(), loginUserVO.getId());
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    @Operation(summary="获取当前登录用户信息")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request){
        User user = userService.getLoginUser(request);

        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user,loginUserVO);

        return ResultUtils.success(loginUserVO);
    }

    /**
     * 后台新增用户（含管理员）
     */
    @PostMapping
    @Operation(summary = "添加用户/管理员")
    @AuthCheck(mustRole = "admin")
    @SuppressWarnings("Duplicates")
    public BaseResponse<Long> addUser(@RequestBody UserAddrequest userAddrequest){
//        校验非空
        if(userAddrequest == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }
        String userAccount = userAddrequest.getUserAccount();
        String userPassword = userAddrequest.getUserPassword();
        String userRole = userAddrequest.getUserRole();
        if(StringUtils.isAnyBlank(userAccount,userPassword,userRole)){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"账号、密码、角色都不能为空");
        }
//      角色白名单校验
        if(!"admin".equals(userRole) && !"user".equals(userRole)){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"用户角色非法，仅支持“admin”或“user”");
        }

//        校验账号是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount",userAccount);
        long count = userService.count(queryWrapper);
        if(count>0){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"该账号已存在！");
        }
        String encryptPassword = PasswordUtils.encryptPassword(userPassword);
        String accessKey = KeyUtils.generateAccessKey();
        String secretKey = KeyUtils.generateSecretKey();
        User newUser = new User();
        newUser.setUserAccount(userAccount);
        newUser.setUserPassword(encryptPassword);
        newUser.setAccessKey(accessKey);
        newUser.setSecretKey(secretKey);
        newUser.setUserRole(userRole);
        boolean result = userService.save(newUser);
        if(!result){
            return ResultUtils.error(ErrorCode.OPERATION_ERROR,"新增 用户/管理员 失败！数据库操作异常");
        }
        log.info("创建新用户成功！用户id：{},用户账号{},用户角色{}",newUser.getId(),userAccount,userRole);
        return ResultUtils.success(newUser.getId());



    }

}
