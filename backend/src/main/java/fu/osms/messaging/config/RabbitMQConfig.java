package fu.osms.messaging.config;

import fu.osms.messaging.constants.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares the RabbitMQ topology. Exchanges, queues and bindings are exposed
 * as {@link Declarables} (and individual exchange beans) so that
 * {@code RabbitAdmin} auto-declares them on the broker. They must NOT be
 * nested inside {@code Map} beans: {@code RabbitAdmin} only auto-declares
 * beans of type {@code Declarable}, {@code Declarables} and collections it
 * can find in the application context.
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public TopicExchange osmsTopicExchange() {
        return new TopicExchange(RabbitMQConstants.TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange osmsDlxExchange() {
        return new DirectExchange(RabbitMQConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderStockWaitingContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        return factory;
    }

    @Bean
    public Declarables osmsDeclarables(TopicExchange osmsTopicExchange, DirectExchange osmsDlxExchange) {
        List<org.springframework.amqp.core.Declarable> declarables = new ArrayList<>();
        for (QueueSpec spec : queueSpecs()) {
            Queue queue = QueueBuilder.durable(spec.name())
                    .deadLetterExchange(osmsDlxExchange.getName())
                    .deadLetterRoutingKey(dlqName(spec.name()))
                    .build();
            Queue dlq = QueueBuilder.durable(dlqName(spec.name())).build();
            Binding binding = BindingBuilder.bind(queue)
                    .to(osmsTopicExchange)
                    .with(spec.routingKey());
            Binding dlqBinding = BindingBuilder.bind(dlq)
                    .to(osmsDlxExchange)
                    .with(dlqName(spec.name()));
            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(binding);
            declarables.add(dlqBinding);
        }
        return new Declarables(declarables);
    }

    private static String dlqName(String queueName) {
        return RabbitMQConstants.DLQ_PREFIX + queueName;
    }

    private static QueueSpec[] queueSpecs() {
        return new QueueSpec[]{
                new QueueSpec(RabbitMQConstants.QUEUE_WEBHOOK_SYNC_ORDER, RabbitMQConstants.SYNC_ORDER),
                new QueueSpec(RabbitMQConstants.QUEUE_WEBHOOK_SYNC_PRODUCT, RabbitMQConstants.SYNC_PRODUCT),
                new QueueSpec(RabbitMQConstants.QUEUE_PRODUCT_PUSH, RabbitMQConstants.PRODUCT_SYNC_PUSH),
                new QueueSpec(RabbitMQConstants.QUEUE_INVENTORY_PUSH, RabbitMQConstants.INVENTORY_UPDATED),
                new QueueSpec(RabbitMQConstants.QUEUE_ORDER_PULL, RabbitMQConstants.ORDER_PULL_REQUESTED),
                new QueueSpec(RabbitMQConstants.QUEUE_ORDER_STOCK_DELIVERY_LIFECYCLE,
                        RabbitMQConstants.ORDER_STOCK_DELIVERY_LIFECYCLE),
                new QueueSpec(RabbitMQConstants.QUEUE_ORDER_RETURN_WORKFLOW,
                        RabbitMQConstants.ORDER_RETURN_WORKFLOW),
                new QueueSpec(RabbitMQConstants.QUEUE_CHANNEL_PUSH_RETRY,
                        RabbitMQConstants.CHANNEL_PUSH_RETRY),
                new QueueSpec(RabbitMQConstants.QUEUE_ORDER_STOCK_WAITING_RECONCILE,
                        RabbitMQConstants.ORDER_STOCK_WAITING_RECONCILE)
        };
    }

    private record QueueSpec(String name, String routingKey) {
    }
}
