package com.zm.ojbackendgateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class CustomBlockRequestHandler implements BlockRequestHandler {

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange,Throwable e) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{\"code\": 429, \"message\": \"Too Many Requests, please try again later.\"}"));
    }
}


//public class CustomBlockRequestHandler implements BlockRequestHandler {
//
//    private final List<ViewResolver> viewResolvers;
//    private final ViewResolverSupport resolverSupport;
//
//    public CustomBlockRequestHandler(List<ViewResolver> viewResolvers, ViewResolverSupport resolverSupport) {
//        this.viewResolvers = viewResolvers;
//        this.resolverSupport = resolverSupport;
//    }
//
//    @Override
//    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable e) {
//        // 使用viewResolvers和resolverSupport来构建响应
//        // ...
//        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
//                .body(BodyInserters.fromValue("{\"code\": 429, \"message\": \"Too Many Requests, please try again later.\"}"));
//    }
//}