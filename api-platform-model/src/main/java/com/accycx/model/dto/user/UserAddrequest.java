package com.accycx.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "添加用户请求体")
public class UserAddrequest implements Serializable {
    @Schema(description = "用户账号")
    private String userAccount;

    @Schema(description = "用户密码")
    private String userPassword;

    @Schema(description = "用户角色(admin/user)")
    private String userRole;

    @Serial
    private static final long serialVersionUID = 1L;
}
