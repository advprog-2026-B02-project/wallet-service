package id.ac.ui.cs.advprog.bidmart.wallet.config;

import id.ac.ui.cs.advprog.bidmart.wallet.event.AuctionSettledEvent;
import id.ac.ui.cs.advprog.bidmart.wallet.event.AuctionUnsoldEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wallet-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    @Bean
    public ConsumerFactory<String, AuctionSettledEvent> auctionSettledConsumerFactory() {
        JsonDeserializer<AuctionSettledEvent> deserializer = new JsonDeserializer<>(AuctionSettledEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuctionSettledEvent> auctionSettledListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AuctionSettledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionSettledConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, AuctionUnsoldEvent> auctionUnsoldConsumerFactory() {
        JsonDeserializer<AuctionUnsoldEvent> deserializer = new JsonDeserializer<>(AuctionUnsoldEvent.class, false);
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuctionUnsoldEvent> auctionUnsoldListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AuctionUnsoldEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionUnsoldConsumerFactory());
        return factory;
    }
}
