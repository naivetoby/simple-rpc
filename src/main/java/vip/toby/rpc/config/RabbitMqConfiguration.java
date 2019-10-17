package vip.toby.rpc.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMqConfiguration
 *
 * @author toby
 */
@Configuration
public class RabbitMqConfiguration {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Bean
    public RabbitAdmin rabbitAdmin() {
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

}
