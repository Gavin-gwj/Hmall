package com.hmall.gateway.routers;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicRouteLoader {
    private final NacosConfigManager nacosConfigManager;
    private final String dataId = "gateway-router.json";
    private final String group = "DEFAULT_GROUP";
    private final Set< String> routeIds = new HashSet<>();
    private final RouteDefinitionWriter writer;
    @PostConstruct
    public void initRouterConfigListener() throws NacosException {
        //1.项目启动先拉取一次配置，并添加配置监听器
        log.info("初始化动态路由监听器");
        String configInfo = nacosConfigManager.getConfigService()
                .getConfigAndSignListener(dataId, group, 5000, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        //2.监听到配置变化，更新路由表
                        updateConfigInfo(configInfo);
                    }
                });
        //3.第一次读取到配置，也需要更新到路由表
        updateConfigInfo(configInfo);
    }

    public void updateConfigInfo(String configInfo) {
        log.debug("监听到配置信息：{}", configInfo);
        //1.解析配置文件
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);
        log.info("更新动态路由表：{}", routeDefinitions);
        //2.删除旧的路由表
        for (String routeId : routeIds) {
            writer.delete(Mono.just(routeId)).subscribe();
            log.info("删除路由表：{}", routeId);
        }
        routeIds.clear();
        log.info("清空路由表");

        //3.更新新的路由表
        for (RouteDefinition routeDefinition : routeDefinitions){
            writer.save(Mono.just(routeDefinition)).subscribe();
            log.info("添加路由表：{}", routeDefinition);
            //记录路由id，便于下一次更新时删除
            routeIds.add(routeDefinition.getId());
            log.info("记录路由表：{}", routeDefinition.getId());
        }
    }
}
