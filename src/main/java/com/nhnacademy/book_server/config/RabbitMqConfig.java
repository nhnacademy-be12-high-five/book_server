package com.nhnacademy.book_server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 1. 큐 이름 상수 (상수로 관리하는 게 실수 방지에 좋음)
    public static final String POINT_QUEUE = "point-queue";
    public static final String POINT_EXCHANGE = "point-exchange";
    public static final String ROUTING_KEY = "point-route";

    // 2. 큐 생성 (우편함)
    @Bean
    public Queue pointQueue() {
        return new Queue(POINT_QUEUE, true); // true: 서버 재시작해도 큐 유지 (Durable)
    }

    // 3. 교환기 생성 (우체국)
    @Bean
    public DirectExchange pointExchange() {
        return new DirectExchange(POINT_EXCHANGE);
    }

    // 4. 바인딩 (우체국과 우편함 연결)
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    // 5. ★중요★ 메시지 컨버터 (JSON으로 보내고 받기 위해 필수)
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
