package com.accycx.common.utils;
import org.springframework.util.DigestUtils;
/**
 * 密码加密工具类
 */
public class PasswordUtils {

//    盐值（Salt），用于混淆密码
//    随便写一段复杂的字符串，不能泄漏给外部
    private static final String SALT = "api_platform_AccyCx_2026";

    /**
     * MD5 加密带盐密码
     * @param userPassword 用户在前端输入的明文密码
     * @return 加密后的32位密文
     */
    public static String encryptPassword(String userPassword){
//        将明文密码和盐值拼接在一起，增加复杂度
        String saltedPassword = SALT + userPassword;
//        使用spring自带的工具类转化为MD5十六进制字符串
        return DigestUtils.md5DigestAsHex(saltedPassword.getBytes());
    }

}
