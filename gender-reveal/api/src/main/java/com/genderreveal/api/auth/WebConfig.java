package com.genderreveal.api.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OwnerAuthInterceptor ownerAuthInterceptor;
    private final OwnerPrincipalArgumentResolver ownerPrincipalArgumentResolver;

    public WebConfig(OwnerAuthInterceptor ownerAuthInterceptor,
                      OwnerPrincipalArgumentResolver ownerPrincipalArgumentResolver) {
        this.ownerAuthInterceptor = ownerAuthInterceptor;
        this.ownerPrincipalArgumentResolver = ownerPrincipalArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAuthInterceptor)
            .addPathPatterns("/api/pages", "/api/auth/me", "/api/owner/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(ownerPrincipalArgumentResolver);
    }
}
