package dslab.broker.exchanges;

import java.util.concurrent.BlockingQueue;

public interface Exchange {

    // Returns the type of the exchange (e.g., direct, fanout, topic, default)
    String getType();

    // Binds a message queue to the exchange with the specified binding key
    void bindToQueue(String key, BlockingQueue<String> queue);

    // Publishes a message to the exchange, routing it based on the exchange type and routing key
    void publishMessage(String routingKey, String message);
}
