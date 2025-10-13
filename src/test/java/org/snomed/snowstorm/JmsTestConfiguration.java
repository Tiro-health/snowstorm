package org.snomed.snowstorm;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

@Configuration
@EnableJms
public class JmsTestConfiguration {

    @Bean
    public ConnectionFactory connectionFactory() {
        // Use vm:// with broker auto-creation
        // broker.persistent=false ensures in-memory broker
        // broker.useJmx=false disables JMX to avoid conflicts
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
        factory.setTrustAllPackages(true);
        // Create a caching connection factory to improve performance
        CachingConnectionFactory cachingFactory = new CachingConnectionFactory(factory);
        cachingFactory.setSessionCacheSize(10);
        return cachingFactory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDeliveryPersistent(false);
        template.setExplicitQosEnabled(true);
        // Ensure messages are sent immediately
        template.setPubSubDomain(false);
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency("1-1");
        factory.setSessionTransacted(false);
        factory.setAutoStartup(true);
        // Reduce recovery interval for faster message delivery in tests
        factory.setRecoveryInterval(100L);
        return factory;
    }
}
