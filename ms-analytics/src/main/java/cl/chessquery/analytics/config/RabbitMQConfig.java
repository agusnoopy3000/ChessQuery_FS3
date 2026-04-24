package cl.chessquery.analytics.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para MS-Analytics.
 *
 * Colas:
 *   - game.events  → binding "game.*" (recibe game.finished, elo.updated)
 *   - etl.events   → binding "etl.*" y "rating.*"
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE         = "ChessEvents";
    public static final String GAME_EVENTS_QUEUE = "game.events";
    public static final String ETL_EVENTS_QUEUE  = "etl.events";

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue gameEventsQueue() {
        return QueueBuilder.durable(GAME_EVENTS_QUEUE).build();
    }

    @Bean
    public Queue etlEventsQueue() {
        return QueueBuilder.durable(ETL_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding gameEventsBinding(Queue gameEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(gameEventsQueue).to(chessEventsExchange).with("game.*");
    }

    @Bean
    public Binding etlEventsBindingEtl(Queue etlEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(etlEventsQueue).to(chessEventsExchange).with("etl.*");
    }

    @Bean
    public Binding etlEventsBindingRating(Queue etlEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(etlEventsQueue).to(chessEventsExchange).with("rating.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
