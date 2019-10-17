package vip.toby.rpc.client;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.FactoryBean;
import vip.toby.rpc.entity.RpcType;

import java.lang.reflect.Proxy;

/**
 * RpcClientProxyFactory
 *
 * @author toby
 */
public class RpcClientProxyFactory implements FactoryBean {

    private final Class<?> rpcClientInterface;
    private final RabbitTemplate sender;
    private final RpcType rpcType;

    public RpcClientProxyFactory(Class<?> rpcClientInterface, RabbitTemplate sender, RpcType rpcType) {
        this.rpcClientInterface = rpcClientInterface;
        this.sender = sender;
        this.rpcType = rpcType;
    }

    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(rpcClientInterface.getClassLoader(), new Class[]{rpcClientInterface}, new RpcClientProxy(rpcClientInterface, rpcType, sender));
    }

    @Override
    public Class<?> getObjectType() {
        return rpcClientInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
