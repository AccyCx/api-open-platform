package com.accycx.common.utils;


import java.util.UUID;

/**
 * API密钥生成工具类
 */
public class KeyUtils {

    /**
     * 生成AccessKey
     * 特点：必须全局唯一，使用去掉横岗的UUID
     *
     * @return 32位随机字符串
     */
    public static String generateAccessKey(){
        return UUID.randomUUID().toString().replace("-","");
    }

    /**
     * 生成SecretKey
     * 特点：必须全局唯一，还要足够复杂防破解
     * 方案：生成一个UUID，然后套一层MD5加密，增加复杂度
     *
     * @return 32位复杂哈希字符串
     */
    public static String generateSecretKey(){
//        先生成一个基础的随机UUID
        String rawKey = UUID.randomUUID().toString().replace("-","");
//        复用PasswordUtils 进行加盐MD5混淆
        return PasswordUtils.encryptPassword(rawKey);
    }

}
