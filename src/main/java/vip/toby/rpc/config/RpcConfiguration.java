package vip.toby.rpc.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vip.toby.rpc.client.RpcClientScanner;
import vip.toby.rpc.server.RpcServerPostProcessor;

/**
 * RpcConfiguration
 *
 * @author toby
 */
@Configuration
public class RpcConfiguration {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange syncDirectExchange() {
        return new DirectExchange("simple.rpc.sync", true, false);
    }

    @Bean
    public DirectExchange syncReplyDirectExchange() {
        return new DirectExchange("simple.rpc.sync.reply", true, false);
    }

    @Bean
    public DirectExchange asyncDirectExchange() {
        return new DirectExchange("simple.rpc.async", true, false);
    }

    @Bean
    public RpcServerPostProcessor rpcServerPostProcessor(ConnectionFactory connectionFactory, DirectExchange syncDirectExchange, DirectExchange asyncDirectExchange) {
        return new RpcServerPostProcessor(connectionFactory, syncDirectExchange, asyncDirectExchange);
    }

    @Bean
    public RpcClientScanner rpcClientScanner(ConnectionFactory connectionFactory, DirectExchange syncReplyDirectExchange) {
        return new RpcClientScanner(connectionFactory, syncReplyDirectExchange);
    }

}
