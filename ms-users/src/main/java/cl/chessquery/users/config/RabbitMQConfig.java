package cl.chessquery.users.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para MS-Users.
 *
 * Exchange:  ChessEvents (Topic) — declarado también en init-rabbitmq.sh.
 *            Spring AMQP es idempotente al re-declararlo.
 *
 * Colas:
 *   - users.elo.queue (exclusiva de este servicio) con binding "elo.*"
 *     para recibir eventos elo.updated publicados por MS-Game.
 *     NOTA: NO se usa la cola game.events porque esa cola ya la consume
 *     MS-Game. Tener dos consumidores distintos en la misma cola causaría
 *     un patrón "competing consumers" donde solo uno recibiría el mensaje.
 *     La solución correcta es una cola dedicada por microservicio consumidor.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE        = "ChessEvents";
    public static final String USERS_ELO_QUEUE = "users.elo.queue";

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    /** Cola durable dedicada a MS-Users para eventos elo.* */
    @Bean
    public Queue usersEloQueue() {
        return QueueBuilder.durable(USERS_ELO_QUEUE).build();
    }

    @Bean
    public Binding usersEloBinding(Queue usersEloQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(usersEloQueue).to(chessEventsExchange).with("elo.*");
    }

    /** Serialización/deserialización JSON para mensajes RabbitMQ. */
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
