package cl.chessquery.notifications.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para MS-Notifications.
 *
 * Colas:
 *   - user.events              → binding "user.*"
 *   - tournament.events        → binding "tournament.*" y "player.*"
 *   - notifications.game.events → binding "elo.*"  (cola dedicada para este servicio)
 *   - etl.events               → binding "etl.*"
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE                = "ChessEvents";
    public static final String USER_EVENTS_QUEUE       = "user.events";
    public static final String TOURNAMENT_EVENTS_QUEUE = "tournament.events";
    public static final String NOTIF_GAME_EVENTS_QUEUE = "notifications.game.events";
    public static final String ETL_EVENTS_QUEUE        = "etl.events";

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue userEventsQueue() {
        return QueueBuilder.durable(USER_EVENTS_QUEUE).build();
    }

    @Bean
    public Queue tournamentEventsQueue() {
        return QueueBuilder.durable(TOURNAMENT_EVENTS_QUEUE).build();
    }

    @Bean
    public Queue notifGameEventsQueue() {
        return QueueBuilder.durable(NOTIF_GAME_EVENTS_QUEUE).build();
    }

    @Bean
    public Queue etlEventsQueue() {
        return QueueBuilder.durable(ETL_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding userEventsBinding(Queue userEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(userEventsQueue).to(chessEventsExchange).with("user.*");
    }

    @Bean
    public Binding tournamentEventsBindingTournament(Queue tournamentEventsQueue,
                                                     TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(tournamentEventsQueue).to(chessEventsExchange).with("tournament.*");
    }

    @Bean
    public Binding tournamentEventsBindingPlayer(Queue tournamentEventsQueue,
                                                 TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(tournamentEventsQueue).to(chessEventsExchange).with("player.*");
    }

    @Bean
    public Binding notifGameEventsBinding(Queue notifGameEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(notifGameEventsQueue).to(chessEventsExchange).with("elo.*");
    }

    @Bean
    public Binding etlEventsBinding(Queue etlEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(etlEventsQueue).to(chessEventsExchange).with("etl.*");
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
