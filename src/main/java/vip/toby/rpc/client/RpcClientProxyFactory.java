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

    private Class<?> rpcClientInterface;
    private String rpcName;
    private RpcType rpcType;
    private RabbitTemplate sender;

    public void setRpcClientInterfaceName(Class<?> rpcClientInterface) {
        this.rpcClientInterface = rpcClientInterface;
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
        return Proxy.newProxyInstance(this.rpcClientInterface.getClassLoader(), new Class[]{this.rpcClientInterface}, new RpcClientProxy(this.rpcClientInterface, this.rpcName, this.rpcType, this.sender));
    }

    @Override
    public Class<?> getObjectType() {
        return this.rpcClientInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
