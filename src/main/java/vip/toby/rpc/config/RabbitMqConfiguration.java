package vip.toby.rpc.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.UUID;

/**
 * RabbitMqConfiguration
 *
 * @author toby
 */
@Configuration
public class RabbitMqConfiguration {

    @Value("${spring.rabbitmq.max-attempts:3}")
    private int maxAttempts;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public SimpleRetryPolicy simpleRetryPolicy() {
        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
        simpleRetryPolicy.setMaxAttempts(maxAttempts);
        return simpleRetryPolicy;
    }

    @Bean
    public RetryTemplate retryTemplate(SimpleRetryPolicy simpleRetryPolicy) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(simpleRetryPolicy);
        return retryTemplate;
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
    public String rabbitClientId() {
        return UUID.randomUUID().toString();
    }
}
