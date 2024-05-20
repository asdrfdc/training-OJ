package com.zm.oj.aop;

import com.zm.oj.AuthCheck;
import com.zm.oj.common.ErrorCode;
import com.zm.oj.exception.BusinessException;
import com.zm.oj.model.entity.User;
import com.zm.oj.model.enums.UserRoleEnum;
import com.zm.oj.service.UserService;
import javax.Resource;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Around;
import org.aspectj.lang.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验 AOP
 *
 *
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint
     * @param authCheck
     * @return
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        //RequestAttributes接口代表了与当前请求关联的所有属性，包括请求和响应对象
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        //用ServletRequestAttributes实现了RequestAttributes并包装了HttpServletRequest和HttpServletResponse。
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 必须有该权限才通过
        if (StringUtils.isNotBlank(mustRole)) {

            UserRoleEnum mustUserRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
            if (mustUserRoleEnum == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            String userRole = loginUser.getUserRole();
            // 如果被封号，直接拒绝
            if (UserRoleEnum.BAN.equals(UserRoleEnum.getEnumByValue(userRole))) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 必须有管理员权限
            if (UserRoleEnum.ADMIN.equals(mustUserRoleEnum)) {
                if (!mustRole.equals(userRole)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
                }
            }
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
//        if (StringUtils.isNotBlank(mustRole)) {
//
//            UserRoleEnum mustUserRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
//            if (mustUserRoleEnum == null) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
//            String userRole = loginUser.getUserRole();
//            // 如果被封号，直接拒绝
//            if (UserRoleEnum.BAN.equals(mustUserRoleEnum)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
//            // 必须有管理员权限
//            if (UserRoleEnum.ADMIN.equals(mustUserRoleEnum)) {
//                if (!mustRole.equals(userRole)) {
//                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//                }
//            }
//        }

    }
}

