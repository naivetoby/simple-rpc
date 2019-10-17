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
    private final String rpcName;
    private final RpcType rpcType;
    private final RabbitTemplate sender;

    public RpcClientProxyFactory(Class<?> rpcClientInterface, String rpcName, RpcType rpcType, RabbitTemplate sender) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
    }

    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(rpcClientInterface.getClassLoader(), new Class[]{rpcClientInterface}, new RpcClientProxy(rpcClientInterface, rpcName, rpcType, sender));
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
