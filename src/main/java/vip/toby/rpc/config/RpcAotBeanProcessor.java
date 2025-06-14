package vip.toby.rpc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.client.RpcClientScannerAotContribution;
import vip.toby.rpc.client.RpcClientScannerConfigurer;
import vip.toby.rpc.server.RpcServerBeanRegistrationContribution;

/**
 * RPC AOT Bean注册处理器
 * 在编译时处理所有RPC相关的Bean，为AOT/Native模式提供必要的运行时提示
 *
 * @author toby
 */
@Slf4j
public class RpcAotBeanProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        Class<?> beanClass = registeredBean.getBeanClass();


        // 1. 处理 RpcClientScannerConfigurer - 触发AOT扫描
        if (beanClass == RpcClientScannerConfigurer.class) {
            log.debug("Processing RpcClientScannerConfigurer for AOT scanning");
            return new RpcClientScannerAotContribution(registeredBean);
        }

        // 2. 处理 @RpcServer 类 - 需要反射访问
        if (beanClass.isAnnotationPresent(RpcServer.class)) {
            log.debug("Processing @RpcServer class for AOT: {}", beanClass.getName());
            return new RpcServerBeanRegistrationContribution(registeredBean);
        }

        return null;
    }
}
