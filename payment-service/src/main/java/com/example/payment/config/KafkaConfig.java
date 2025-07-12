package com.example.payment.config;

import com.example.common.kafka.CommonKafkaConfig;
import com.example.payment.dto.OrderRequest;
import com.example.payment.dto.PaymentResult;
import com.example.payment.dto.PaymentRequestWithSaga;
import com.example.payment.dto.PaymentCancelRequest;
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
    public ProducerFactory<String, PaymentResult> paymentResultProducerFactory() {
        return CommonKafkaConfig.createProducerFactory(bootstrapServers);
    }

    @Bean
    public ProducerFactory<String, Object> objectProducerFactory() {
        return CommonKafkaConfig.createProducerFactory(bootstrapServers);
    }

    @Bean
    public ConsumerFactory<String, OrderRequest> orderRequestConsumerFactory() {
        return CommonKafkaConfig.createConsumerFactory(
            bootstrapServers,
            "payment-group",
            OrderRequest.class,
            "com.example.payment.dto",
            "com.example.order.dto"
        );
    }

    @Bean
    public ConsumerFactory<String, PaymentRequestWithSaga> sagaRequestConsumerFactory() {
        return CommonKafkaConfig.createConsumerFactory(
            bootstrapServers,
            "payment-saga-group",
            PaymentRequestWithSaga.class,
            "com.example.payment.dto",
            "com.example.order.dto"
        );
    }

    @Bean
    public ConsumerFactory<String, PaymentCancelRequest> cancelRequestConsumerFactory() {
        return CommonKafkaConfig.createConsumerFactory(
            bootstrapServers,
            "payment-cancel-group",
            PaymentCancelRequest.class,
            "com.example.payment.dto"
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderRequest> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderRequestConsumerFactory());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRequestWithSaga> sagaKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentRequestWithSaga> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaRequestConsumerFactory());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCancelRequest> cancelKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCancelRequest> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cancelRequestConsumerFactory());
        return factory;
    }

    @Bean("paymentResultKafkaTemplate")
    public KafkaTemplate<String, PaymentResult> kafkaTemplate() {
        return new KafkaTemplate<>(paymentResultProducerFactory());
    }

    @Bean("paymentSagaKafkaTemplate")
    public KafkaTemplate<String, Object> sagaKafkaTemplate() {
        return new KafkaTemplate<>(objectProducerFactory());
    }
} 