package com.accycx.backend.service.impl;


import com.accycx.backend.mapper.UserMapper;
import com.accycx.backend.service.UserService;
import com.accycx.common.utils.JwtUtils;
import com.accycx.common.utils.KeyUtils;
import com.accycx.common.utils.PasswordUtils;
import com.accycx.model.entity.User;
import com.accycx.model.vo.user.LoginUserVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

//    用户注册逻辑
    @Override
    public long userRegister(String userAccount,String userPassword,String checkPassword){

//        1.校验参数是否为空
        if(StringUtils.isAnyBlank(userAccount,userPassword,checkPassword)){
            throw new RuntimeException("参数不能为空");
        }

//        2.账号长度不能小于4位，密码不能小于8位
        if(userAccount.length()<4 || userPassword.length()<8){
            throw new RuntimeException("账号过短或密码过短");
        }

//        3.校验两次输入的密码是否一致
        if(!userPassword.equals(checkPassword)){
            throw new RuntimeException("两次输入的密码不一致");
        }

//        4.检查账号是否重复（数据库里查）
//        注意:高并发场景下这里其实是不够的，必须配合数据库 user_account 字段的唯一索引来防重
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account",userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if(count>0){
            throw new RuntimeException("账号重复");
        }

//        5.密码加密
        String encryptPassword = PasswordUtils.encryptPassword(userPassword);

//        6.颁发API调用的AK/SK
        String accessKey = KeyUtils.generateAccessKey();
        String secretKey = KeyUtils.generateSecretKey();

//        7.将数据插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setAccessKey(accessKey);
        user.setSecretKey(secretKey);

//        MyBatis-Plus 的 save 方法会自动填充 createTime 和 updateTime 字段
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new RuntimeException("注册失败，数据库错误");
        }

        return user.getId();
    }

//    用户登录逻辑
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword){
//        1.校验非空
        if(StringUtils.isAnyBlank(userAccount,userPassword)){
            throw new RuntimeException("账号和密码不能为空");
        }

//        2.密码加密(将前端传来的明文密码进行加密，再去和数据库里的比对)
        String encryptPassword = PasswordUtils.encryptPassword(userPassword);

//        3.查询数据库是否存在该用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account",userAccount);
        queryWrapper.eq("user_password",encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if(user == null){
            throw new RuntimeException("账号或密码错误");
        }

//        4.账号密码正确，生成JWT Token
        String token = JwtUtils.generateToken(user.getId(),user.getUserAccount());

//        5.封装返回脱敏数据（VO）

        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        loginUserVO.setToken(token);

        return loginUserVO;

    }

//    获取当前登录用户的信息
    @Override
    public User getLoginUser(HttpServletRequest request){

//        1.从请求头获取Token（前端放在Authorization字段或token字段）
        String token = request.getHeader("Authorization");
        if(StringUtils.isBlank(token)){
//            兼容前端可能放在token字段里
            token = request.getHeader("token");
        }
        if(StringUtils.isBlank(token)){
            throw new RuntimeException("未登录：Token为空");
        }

//        2.解析Token，获取用户ID（调用之前写的JwtUtils）
        long userId;
        try{
            Claims claims = JwtUtils.parseToken(token);
            userId = claims.get("userId", Number.class).longValue();
        } catch (Exception e) {
            throw new RuntimeException("未登录：Token不合法或已过期");
        }

//        3.从数据库查询最新信息，确保AK/SK等敏感信息是最新的（如果用户被管理员禁用或删除了，这里也能查不到，保证安全性）
        User currentUser = this.getById(userId);
        if(currentUser == null){
            throw new RuntimeException("用户不存在");
        }

        return currentUser;
    }
}
