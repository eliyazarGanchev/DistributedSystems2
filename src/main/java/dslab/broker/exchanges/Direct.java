package dslab.broker.exchanges;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Direct implements Exchange {

    @Getter
    private final String name;
    private final ConcurrentHashMap<String, List<BlockingQueue<String>>> map = new ConcurrentHashMap<>();
    public static final String TYPE = "direct";

    public Direct(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void bindToQueue(String bindingKey, BlockingQueue<String> queue) {
        List<BlockingQueue<String>> queues = map.get(bindingKey);
        if (queues == null) {
            queues = new ArrayList<>();
            map.put(bindingKey, queues);
        }
        if (!queues.contains(queue)) {
            queues.add(queue);
        }
    }

    @Override
    public void publishMessage(String routingKey, String message) {
        List<BlockingQueue<String>> boundQueues = map.get(routingKey);
        if (boundQueues != null) {
            for (BlockingQueue<String> queue : boundQueues) {
                queue.offer(message);
            }
        }
    }
}
