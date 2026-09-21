package com.genderreveal.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class OwnerPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OwnerPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object principal = webRequest.getAttribute(OwnerPrincipal.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (principal == null) {
            throw new IllegalStateException("OwnerPrincipal requested on a route not guarded by OwnerAuthInterceptor");
        }
        return principal;
    }
}
