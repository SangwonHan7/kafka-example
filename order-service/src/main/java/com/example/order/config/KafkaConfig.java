package com.example.order.config;

import com.example.common.kafka.CommonKafkaConfig;
import com.example.order.dto.OrderRequest;
import com.example.order.dto.PaymentResult;
import com.example.order.dto.PaymentResultWithSaga;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, OrderRequest> orderRequestProducerFactory() {
        return CommonKafkaConfig.createProducerFactory(bootstrapServers);
    }

    @Bean
    public ProducerFactory<String, Object> objectProducerFactory() {
        return CommonKafkaConfig.createProducerFactory(bootstrapServers);
    }

    @Bean
    public ConsumerFactory<String, PaymentResult> paymentResultConsumerFactory() {
        return CommonKafkaConfig.createConsumerFactory(
            bootstrapServers,
            "order-group",
            PaymentResult.class,
            "com.example.order.dto",
            "com.example.payment.dto"
        );
    }
    
    @Bean
    public ConsumerFactory<String, PaymentResultWithSaga> sagaResultConsumerFactory() {
        return CommonKafkaConfig.createConsumerFactory(
            bootstrapServers,
            "order-saga-group",
            PaymentResultWithSaga.class,
            "com.example.order.dto",
            "com.example.payment.dto"
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentResult> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentResult> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentResultConsumerFactory());
        return factory;
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentResultWithSaga> sagaResultKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentResultWithSaga> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaResultConsumerFactory());
        return factory;
    }

    @Bean("orderRequestKafkaTemplate")
    public KafkaTemplate<String, OrderRequest> kafkaTemplate() {
        return new KafkaTemplate<>(orderRequestProducerFactory());
    }

    @Bean("sagaKafkaTemplate")
    public KafkaTemplate<String, Object> sagaKafkaTemplate() {
        return new KafkaTemplate<>(objectProducerFactory());
    }
} 