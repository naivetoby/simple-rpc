package vip.toby.rpc.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMqConfiguration
 *
 * @author toby
 */
@ConditionalOnProperty(prefix = "simple-rpc", name = "mode")
@Configuration
public class RabbitMqConfiguration {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

}
