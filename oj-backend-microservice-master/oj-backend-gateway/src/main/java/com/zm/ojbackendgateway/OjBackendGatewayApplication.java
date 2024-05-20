package com.zm.ojbackendgateway;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.zm.ojbackendgateway.handler.CustomBlockRequestHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
public class OjBackendGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OjBackendGatewayApplication.class, args);
    }

    private static void initCustomBlockHandler() {
        GatewayCallbackManager.setBlockHandler(new CustomBlockRequestHandler()::handleRequest);
    }

}
