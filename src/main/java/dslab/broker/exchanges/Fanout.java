package dslab.broker.exchanges;

import lombok.Getter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Fanout implements Exchange {

    @Getter
    private final String name;
    private final ConcurrentLinkedQueue<BlockingQueue<String>> queues = new ConcurrentLinkedQueue<>();
    public static final String TYPE = "fanout";

    public Fanout(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void bindToQueue(String bindingKey, BlockingQueue<String> queue) {
        if (!queues.contains(queue)) {
            queues.add(queue);
        }
    }

    @Override
    public void publishMessage(String routingKey, String message) {
        for (BlockingQueue<String> queue : queues) {
            queue.offer(message);
        }
    }
}
