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
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
//        1.校验费控
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
        loginUserVO.setId(user.getId());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setToken(token);

        return loginUserVO;

    }
}
