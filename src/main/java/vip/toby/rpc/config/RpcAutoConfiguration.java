package vip.toby.rpc.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Objects;

/**
 * RabbitAutoConfiguration
 *
 * @author toby
 */
@Configuration
@AutoConfigureBefore(RabbitAutoConfiguration.class)
@EnableConfigurationProperties(RabbitProperties.class)
@Import({RpcDefinitionRegistrar.class})
public class RpcAutoConfiguration {

    @Configuration
    @ConditionalOnMissingBean(ConnectionFactory.class)
    protected static class RabbitConnectionFactoryCreator {

        @Bean
        public static CachingConnectionFactory rabbitConnectionFactory(RabbitProperties properties, ObjectProvider<ConnectionNameStrategy> connectionNameStrategy) throws Exception {
            PropertyMapper map = PropertyMapper.get();
            CachingConnectionFactory factory = new CachingConnectionFactory(Objects.requireNonNull(getRabbitConnectionFactoryBean(properties).getObject()));
            map.from(properties::determineAddresses).to(factory::setAddresses);
            map.from(properties::isPublisherConfirms).to(factory::setPublisherConfirms);
            map.from(properties::isPublisherReturns).to(factory::setPublisherReturns);
            RabbitProperties.Cache.Channel channel = properties.getCache().getChannel();
            map.from(channel::getSize).whenNonNull().to(factory::setChannelCacheSize);
            map.from(channel::getCheckoutTimeout).whenNonNull().as(Duration::toMillis).to(factory::setChannelCheckoutTimeout);
            RabbitProperties.Cache.Connection connection = properties.getCache().getConnection();
            map.from(connection::getMode).whenNonNull().to(factory::setCacheMode);
            map.from(connection::getSize).whenNonNull().to(factory::setConnectionCacheSize);
            map.from(connectionNameStrategy::getIfUnique).whenNonNull().to(factory::setConnectionNameStrategy);
            return factory;
        }

        private static RabbitConnectionFactoryBean getRabbitConnectionFactoryBean(RabbitProperties properties) throws Exception {
            PropertyMapper map = PropertyMapper.get();
            RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
            map.from(properties::determineHost).whenNonNull().to(factory::setHost);
            map.from(properties::determinePort).to(factory::setPort);
            map.from(properties::determineUsername).whenNonNull().to(factory::setUsername);
            map.from(properties::determinePassword).whenNonNull().to(factory::setPassword);
            map.from(properties::determineVirtualHost).whenNonNull().to(factory::setVirtualHost);
            map.from(properties::getRequestedHeartbeat).whenNonNull().asInt(Duration::getSeconds).to(factory::setRequestedHeartbeat);
            RabbitProperties.Ssl ssl = properties.getSsl();
            if (ssl.isEnabled()) {
                factory.setUseSSL(true);
                map.from(ssl::getAlgorithm).whenNonNull().to(factory::setSslAlgorithm);
                map.from(ssl::getKeyStoreType).to(factory::setKeyStoreType);
                map.from(ssl::getKeyStore).to(factory::setKeyStore);
                map.from(ssl::getKeyStorePassword).to(factory::setKeyStorePassphrase);
                map.from(ssl::getTrustStoreType).to(factory::setTrustStoreType);
                map.from(ssl::getTrustStore).to(factory::setTrustStore);
                map.from(ssl::getTrustStorePassword).to(factory::setTrustStorePassphrase);
                map.from(ssl::isValidateServerCertificate).to((validate) -> factory.setSkipServerCertificateValidation(!validate));
                map.from(ssl::getVerifyHostname).to(factory::setEnableHostnameVerification);
            }
            map.from(properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis).to(factory::setConnectionTimeout);
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        @ConditionalOnMissingBean
        public static AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

    }

}
