package com.zm.ojbackendgateway.config;

//import com.zm.ojbackendgateway.handler.CustomBlockRequestHandler;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
//import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
//import org.springframework.boot.web.reactive.error.ErrorAttributes;
//import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.util.Assert;
//import org.springframework.web.reactive.result.view.ViewResolver;
//import org.springframework.web.reactive.result.view.ViewResolverSupport;
//import org.springframework.web.reactive.result.view.ViewResolverRegistry;
//import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer;
//import org.springframework.web.reactive.result.view.freemarker.FreeMarkerViewResolver;
//
//import java.util.List;
//import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自定义限流异常处理器并通过viewResolvers和serverCodecConfigurer定制返回结果（不过有点问题，以后再优化）
 */
//@Configuration
public class CustomBlockRequestHandlerConfig {

//    @Autowired
//    private ApplicationContext applicationContext;
//
//    @Bean
//    public CustomBlockRequestHandler customBlockRequestHandler() {
//        List<ViewResolver> viewResolvers = new CopyOnWriteArrayList<>();
//        for (ViewResolver viewResolver : this.applicationContext.getBeansOfType(ViewResolver.class).values()) {
//            viewResolvers.add(viewResolver);
//        }
//
//        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
//        // ... 配置FreeMarkerConfigurer
//        ViewResolverSupport resolverSupport = new FreeMarkerViewResolver();
//        resolverSupport.setViewResolvers(new ViewResolverRegistry(configurer));
//        resolverSupport.afterPropertiesSet();
//
//        return new CustomBlockRequestHandler(viewResolvers, resolverSupport);
//    }
}


