package dslab.broker.exchanges;

import lombok.Getter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Default implements Exchange {

    @Getter
    private final String name;
    private final ConcurrentHashMap<String, BlockingQueue<String>> map = new ConcurrentHashMap<>();
    public static final String TYPE = "default";

    public Default(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void bindToQueue(String bindingKey, BlockingQueue<String> queue) {
        if (!map.containsKey(bindingKey)) {
            map.put(bindingKey, queue);
        }
    }


    @Override
    public void publishMessage(String routingKey, String message) {
        BlockingQueue<String> queue = map.get(routingKey);
        if (queue != null) {
            queue.offer(message);
        }
    }

    public void handleQueueCreation(String queueName, BlockingQueue<String> queue) {
        if (!map.containsKey(queueName)) {
            bindToQueue(queueName, queue);
        }
    }
}

