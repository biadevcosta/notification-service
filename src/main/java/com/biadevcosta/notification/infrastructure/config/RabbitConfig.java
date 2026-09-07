package com.biadevcosta.notification.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the reminder topology (must match scheduling-service) and a JSON converter that
 * deserializes by the listener's parameter type — ignoring the {@code __TypeId__} header the
 * producer adds with its own (here non-existent) class name.
 */
@Configuration
public class RabbitConfig {

    @Bean
    DirectExchange reminderExchange(ReminderRabbitProperties props) {
        return new DirectExchange(props.exchange());
    }

    @Bean
    Queue reminderQueue(ReminderRabbitProperties props) {
        return QueueBuilder.durable(props.queue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", props.dlq())
                .build();
    }

    @Bean
    Queue reminderDlq(ReminderRabbitProperties props) {
        return QueueBuilder.durable(props.dlq()).build();
    }

    @Bean
    Binding reminderBinding(ReminderRabbitProperties props) {
        return BindingBuilder.bind(reminderQueue(props)).to(reminderExchange(props)).with(props.routingKey());
    }

    @Bean
    MessageConverter rabbitJsonConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter("com.biadevcosta.*");
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
