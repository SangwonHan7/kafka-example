package com.example.payment.config;

import com.example.common.kafka.CommonKafkaConfig;
import com.example.payment.dto.OrderRequest;
import com.example.payment.dto.PaymentResult;
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
    public ConcurrentKafkaListenerContainerFactory<String, OrderRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderRequest> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderRequestConsumerFactory());
        return factory;
    }

    @Bean
    public KafkaTemplate<String, PaymentResult> kafkaTemplate() {
        return new KafkaTemplate<>(paymentResultProducerFactory());
    }
} 