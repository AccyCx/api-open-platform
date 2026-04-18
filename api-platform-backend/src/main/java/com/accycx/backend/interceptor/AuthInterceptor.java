package com.accycx.backend.interceptor;


import com.accycx.backend.service.UserService;
import com.accycx.common.AuthCheck;
import com.accycx.common.enums.ErrorCode;
import com.accycx.common.utils.ResultUtils;
import com.accycx.model.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect //标注这是一个切面类，里面定义了横切逻辑（拦截器）
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

//    执行拦截
    @Around("@annotation(authCheck)") //拦截所有被 @AuthCheck 注解标记的方法
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable{
//        ProceedingJoinPoint joinPoint: 代表被拦截的方法，可以通过它获取方法参数、方法签名等信息，并且可以调用 joinPoint.proceed() 来继续执行被拦截的方法。
//        throws Throwable: 因为 joinPoint.proceed() 可能会抛出任何异常，所以我们需要在方法签名中声明 throws Throwable 来允许这些异常被传播。

        String mustRole = authCheck.mustRole();
//        获取当前请求的 RequestAttributes 对象，这个对象包含了当前 HTTP 请求的上下文信息，比如请求参数、请求头、会话等。我们需要它来获取当前登录用户的信息。
//        RequestContextHolder 是 Spring 提供的一个工具类，用于获取当前线程绑定的 RequestAttributes 对象。它提供了几个静态方法来访问这些对象：
//        currentRequestAttributes()：返回当前线程绑定的 RequestAttributes 对象，如果没有绑定则抛出 IllegalStateException。
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();

//      因为我们知道当前请求是一个 HTTP 请求，所以我们可以把 RequestAttributes 强制转换成 ServletRequestAttributes。
//      ServletRequestAttributes 是 RequestAttributes 的一个子类，专门用于处理 HTTP 请求的上下文信息。它提供了一个 getRequest() 方法，可以直接获取当前的 HttpServletRequest 对象。
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

//        1.获取当前登录用户
        User loginUser = userService.getLoginUser(request);

//        2.必须有管理员权限
        if(StringUtils.isNotBlank(mustRole)){
            String userRole = loginUser.getUserRole();
            if(!mustRole.equals(userRole)){
                return ResultUtils.error(ErrorCode.NO_AUTH_ERROR,"无权限访问");
            }
        }
        return joinPoint.proceed();

    }
}
