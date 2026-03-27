package com.campus.lostfound.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 4, max = 32, message = "账号长度4-32位")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$", message = "账号仅含字母、数字、下划线或中文")
    private String account;

    @NotBlank
    @Size(min = 8, max = 32, message = "密码8-32位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需包含字母和数字")
    private String password;

    /** 可选，默认自动生成 */
    private String nickname;

    /** 0=普通用户，1=微信用户（账号注册仅允许0；选微信用户请走微信登录） */
    @Min(0)
    @Max(1)
    private Integer userType;
}
