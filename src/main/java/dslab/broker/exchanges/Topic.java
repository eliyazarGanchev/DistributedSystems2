package dslab.broker.exchanges;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Topic implements Exchange {

    @Getter
    private final String name;
    private final TrieNode root;
    public static final String TYPE = "topic";

    public Topic(String name) {
        this.name = name;
        this.root = new TrieNode();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void bindToQueue(String bindingKey, BlockingQueue<String> queue) {
        String[] parts = bindingKey.split("\\.");
        TrieNode currNode = root;

        for (String part : parts) {
            if (!currNode.children.containsKey(part)) {
                currNode.children.put(part, new TrieNode());
            }
            currNode = currNode.children.get(part);
        }
        currNode.queues.add(queue);
    }

    @Override
    public void publishMessage(String routingKey, String message) {
        String[] elements = routingKey.split("\\.");
        List<BlockingQueue<String>> matchedQueues = new ArrayList<>();
        getTargetQueues(root, elements, matchedQueues);

        for (BlockingQueue<String> queue : matchedQueues) {
            queue.offer(message);
        }
    }

    private void getTargetQueues(TrieNode root, String[] elements, List<BlockingQueue<String>> queuesMatch) {
        Queue<TrieNode.TraversalState> traversalQueue = new LinkedList<>();
        traversalQueue.add(new TrieNode.TraversalState(root, 0));

        while (!traversalQueue.isEmpty()) {
            TrieNode.TraversalState currentState = traversalQueue.poll();
            TrieNode currentNode = currentState.node;
            int currIndex = currentState.index;

            if (currIndex == elements.length) {
                if (currentNode.children.get("#") != null) {
                    queuesMatch.addAll(currentNode.children.get("#").queues);
                } else {
                    queuesMatch.addAll(currentNode.queues);
                }
                continue;
            }

            String element = elements[currIndex];

            if (currentNode.children.containsKey(element)) {
                traversalQueue.add(new TrieNode.TraversalState(currentNode.children.get(element), currIndex + 1));
            }

            if (currentNode.children.containsKey("*")) {
                traversalQueue.add(new TrieNode.TraversalState(currentNode.children.get("*"), currIndex + 1));
            }

            if (currentNode.children.containsKey("#")) {
                traversalQueue.add(new TrieNode.TraversalState(currentNode.children.get("#"), currIndex));
                traversalQueue.add(new TrieNode.TraversalState(currentNode, currIndex + 1));
            }
        }
    }

    private static class TrieNode {
        Map<String, TrieNode> children = new ConcurrentHashMap<>();
        List<BlockingQueue<String>> queues = new ArrayList<>();

        static class TraversalState {
            TrieNode node;
            int index;

            TraversalState(TrieNode node, int index) {
                this.node = node;
                this.index = index;
            }
        }

    }
}
