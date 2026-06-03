package cl.chessquery.tournament.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de RabbitMQ para MS-Tournament.
 * Solo productor — declara el exchange ChessEvents (idempotente).
 * No consume colas (MS-Tournament solo publica eventos).
 */
@Configuration
@Profile("!test")
public class RabbitMQConfig {

    public static final String EXCHANGE = "ChessEvents";

    /** Cola propia para recibir game.finished de ms-game (resultado de las
     *  partidas en vivo de torneo). Cola dedicada para no competir con
     *  ms-analytics / ms-notifications. */
    public static final String GAME_RESULTS_QUEUE = "tournament.game.results";

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue tournamentGameResultsQueue() {
        return QueueBuilder.durable(GAME_RESULTS_QUEUE).build();
    }

    @Bean
    public Binding gameResultsBinding(Queue tournamentGameResultsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(tournamentGameResultsQueue).to(chessEventsExchange).with("game.finished");
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
}
