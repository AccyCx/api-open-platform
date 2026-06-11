package com.accycx.model.vo.user;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "登录用户返回体")
public class LoginUserVO implements Serializable {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户账号")
    private String userAccount;

    @Schema(description = "用户角色")
    private String userRole;

    @Schema(description = "令牌")
    private String token;//颁发给前端的令牌

    @Schema(description = "公钥")
    private String accessKey;

    @Schema(description = "密钥")
    private String secretKey;

    @Serial
    private static final long serialVersionUID = 1L;
}
