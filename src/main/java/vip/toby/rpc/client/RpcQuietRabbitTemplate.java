package vip.toby.rpc.client;

import com.rabbitmq.client.Channel;
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RpcQuietRabbitTemplate extends RabbitTemplate {

    public RpcQuietRabbitTemplate(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    @Override
    public void onMessage(@NonNull Message message, Channel channel) {
        try {
            super.onMessage(message, channel);
        } catch (AmqpRejectAndDontRequeueException e) {
            if (e.getMessage() != null && e.getMessage().contains("Reply received after timeout")) {
                return;
            }
            throw e;
        }
    }

}
