/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zm.ojbackendgateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eric Zhao
 */
@Configuration
public class GatewayConfiguration {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public GatewayConfiguration(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 当发生BlockException时，这个处理器会捕获异常并进行处理，例如返回特定的错误响应，
     * 这可以通过viewResolvers和serverCodecConfigurer来定制。
     * @return
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        // Register the block exception handler for Spring Cloud Gateway.
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }


    /**
     * 实现限流和降级，根据GatewayFlowRule进行流量控制
     * 如果请求被限流，SentinelGatewayFilter会抛出一个BlockException
     * @return
     */
    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }


    @PostConstruct
    public void doInit() {
        initCustomizedApis();
        initGatewayRules();
    }

    /**
     * 自定义API，设置匹配策略，根据API来识别和管理流量
     * 虽然第二个API匹配所有路径，不过一个请求如果已经匹配了第一个API就不会匹配第二个API，即顺序匹配
     */
    private void initCustomizedApis() {
        Set<ApiDefinition> definitions = new HashSet<>();


        ApiDefinition api1 = new ApiDefinition("some_customized_api")
            .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                //精准匹配
                add(new ApiPathPredicateItem().setPattern("/ahas"));
                //前缀匹配，"/product/**"已经表示了所有以/product开头的请求，与setMatchStrategy的效果相同
                add(new ApiPathPredicateItem().setPattern("/product/**")
                    .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
            }});

        ApiDefinition api2 = new ApiDefinition("another_customized_api")
            .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                add(new ApiPathPredicateItem().setPattern("/**")
                    .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
            }});

        definitions.add(api1);
        definitions.add(api2);

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 定义网关流控规则
     * 如果针对同一个API定义了多个规则如下文所示，那么最严格最复杂的规则会覆盖其他多余的规则，严格指的是减小QPS，增大时间间隔
     */
    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule("alin_route")
                //限制每个时间间隔请求数（QPS）为10
            .setCount(10)
                //间隔时间为1秒
            .setIntervalSec(1)
        );

        rules.add(new GatewayFlowRule("alin_route")
            .setCount(2)
            .setIntervalSec(2)
                //设置了突发量（burst）为2，这意味着在2秒内最多允许2个请求突发，也就是可以处理4个请求，防止请求瞬间激增
            .setBurst(2)
                //基于客户端IP进行流量控制，意味着限制同一IP的并发请求
            .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP)
            )
        );

        rules.add(new GatewayFlowRule("httpbin_route")
            .setCount(10)
            .setIntervalSec(1)
                //控制行为设置为RATE_LIMITER，即线程池限流
            .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)
                //最大排队超时时间设置为600毫秒，如果在600毫秒内没有处理完请求，则直接返回429 Too Many Requests。
                //可以避免请求挤压消耗内存，也可以保证请求及时得到处理
            .setMaxQueueingTimeoutMs(600)
            .setParamItem(new GatewayParamFlowItem()
                    //指定了参数解析策略为从请求头（Header）中获取数据
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                    //设置了要提取的特定Header字段名称，这里是"X-Sentinel-Flag"，如果有该字段就进行限流，还可以设置根据该字段的值是否匹配决定是否限流，详情见后一个
                .setFieldName("X-Sentinel-Flag")
            )
        );

        rules.add(new GatewayFlowRule("httpbin_route")
            .setCount(1)
            .setIntervalSec(1)
                //基于URL参数"pa"进行限流
            .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM)
                .setFieldName("pa")
            )
        );

        rules.add(new GatewayFlowRule("httpbin_route")
            .setCount(2)
            .setIntervalSec(30)
                //基于URL参数，但参数名为"type"，
                // 匹配策略为PARAM_MATCH_STRATEGY_CONTAINS，意味着请求中"type"参数包含"warn"时才应用限流规则。
            .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM)
                .setFieldName("type")
                .setPattern("warn")
                .setMatchStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_CONTAINS)
            )
        );

        //前面的规则都是根据路由规则里面的设置，id即为API名称，但是下面这个是自定义API，所有要多写一个根据API名称进行匹配
        rules.add(new GatewayFlowRule("some_customized_api")
                //资源模式是自定义API名称，这意味着我们不是简单地基于路由路径来定义规则，而是创建了一个逻辑上的API分组，这个分组可以跨越多个实际的路由
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
            .setCount(5)
            .setIntervalSec(1)
            .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM)
                .setFieldName("pn")
            )
        );

        GatewayRuleManager.loadRules(rules);
    }
}
