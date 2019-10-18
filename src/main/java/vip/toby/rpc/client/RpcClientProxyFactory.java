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

    private Class<?> rpcClientClass;
    private String rpcName;
    private RpcType rpcType;
    private RabbitTemplate sender;

    public void setRpcClientClass(Class<?> rpcClientClass) {
        this.rpcClientClass = rpcClientClass;
    }

    public void setRpcName(String rpcName) {
        this.rpcName = rpcName;
    }

    public void setRpcType(RpcType rpcType) {
        this.rpcType = rpcType;
    }

    public void setSender(RabbitTemplate sender) {
        this.sender = sender;
    }

    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(this.rpcClientClass.getClassLoader(), new Class[]{this.rpcClientClass}, new RpcClientProxy(this.rpcClientClass, this.rpcName, this.rpcType, this.sender));
    }

    @Override
    public Class<?> getObjectType() {
        return this.rpcClientClass;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
