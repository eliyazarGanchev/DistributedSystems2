package dslab.broker;

import dslab.ComponentFactory;
import dslab.broker.exchanges.Exchange;
import dslab.config.BrokerConfig;

import dslab.broker.exchanges.Direct;
import dslab.broker.exchanges.Fanout;
import dslab.broker.exchanges.Topic;
import dslab.broker.exchanges.Default;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;

public class Broker implements IBroker {
    private final BrokerConfig config;
    private ServerSocket serverSocket;
    private final ThreadFactory threadFactory;
    private final ConcurrentHashMap<String, Exchange> exchanges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private boolean flag = true;

    public Broker(BrokerConfig config) {
        this.config = config;
        this.threadFactory = new MyThreadFactory();
    }


    @Override
    public void run() {
        createExchange("default", "default");
        DNSConnector dnsConnector = new DNSConnector(config.dnsHost(), config.dnsPort());
        dnsConnector.register(config.domain(), config.host(), config.port());
        try {
            serverSocket = new ServerSocket(config.port());
            while (flag) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadFactory.newThread(new BrokerService(clientSocket)).start();
                } catch (IOException e) {
                    if (flag) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            shutdown();
        }
    }

    public void createExchange(String type, String name) {
        if (exchanges.containsKey(name)) {
            Exchange existingExchange = exchanges.get(name);
            if (!existingExchange.getType().equals(type)) {
                System.out.println("error exchange already exists with different type");
                return;
            }
            return;
        }

        Exchange typeName;
        switch (type) {
            case "direct":
                typeName = new Direct(name);
                break;
            case "fanout":
                typeName = new Fanout(name);
                break;
            case "topic":
                typeName = new Topic(name);
                break;
            case "default":
                typeName = new Default(name);
                break;
            default:
                System.out.println("error");
                return;
        }
        exchanges.put(name, typeName);
    }

    @Override
    public void shutdown() {
        flag = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ComponentFactory.createBroker(args[0]).run();
    }

    private static class MyThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }
    }

    private class BrokerService implements Runnable {
        private final Socket clientSocket;

        public BrokerService(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            processClient(clientSocket);
        }

        private void processClient(Socket clientSocket) {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                out.println("ok SMQP");

                String currExchange = null;
                String currQueue = null;

                String input;
                while ((input = in.readLine()) != null) {
                    String[] commands = input.split(" ");
                    String command = commands[0];

                    switch (command) {
                        case "exchange":
                            if (commands.length != 3) {
                                out.println("error usage: exchange <type> <name>");
                            } else {
                                String exchangeType = commands[1];
                                String exchangeName = commands[2];

                                if (exchanges.containsKey(exchangeName)) {
                                    Exchange existingExchange = exchanges.get(exchangeName);
                                    if (!existingExchange.getType().equals(exchangeType)) {
                                        out.println("error");
                                    } else {
                                        out.println("ok");
                                    }
                                } else {
                                    createExchange(exchangeType, exchangeName);
                                    out.println("ok");
                                }
                                currExchange = exchangeName;
                            }
                            break;

                        case "queue":
                            if (commands.length != 2) {
                                out.println("error usage: queue <name>");
                            } else {
                                currQueue = commands[1];
                                synchronized (queues) {
                                    if (!queues.containsKey(currQueue)) {
                                        BlockingQueue<String> newQueue = new LinkedBlockingQueue<>();
                                        queues.put(currQueue, newQueue);
                                        Exchange exchange = exchanges.get("default");
                                        if (exchange instanceof Default) {
                                            ((Default) exchange).handleQueueCreation(currQueue, newQueue);
                                        }
                                    }
                                }
                                out.println("ok");
                            }
                            break;


                        case "bind":
                            if (commands.length != 2) {
                                out.println("error usage: bind <binding-key>");
                            } else if (currExchange == null) {
                                out.println("error no exchange declared");
                            } else if (currQueue == null) {
                                out.println("error no queue declared");
                            } else {
                                String bindingKey = commands[1];
                                Exchange exchange = exchanges.get(currExchange);
                                BlockingQueue<String> queue = queues.get(currQueue);
                                if (exchange != null && queue != null) {
                                    exchange.bindToQueue(bindingKey, queue);
                                    out.println("ok");
                                } else {
                                    out.println("error");
                                }
                            }
                            break;

                        case "publish":
                            if (commands.length < 3) {
                                out.println("publish <routing-key> <message>");
                            } else if (currExchange == null) {
                                out.println("error no exchange declared");
                            } else {
                                String routingKey = commands[1];

                                StringBuilder messageBuilder = new StringBuilder();
                                int messageStart = 2;
                                for (int i = messageStart; i < commands.length; i++) {
                                    messageBuilder.append(commands[i]).append(" ");
                                }
                                String message = messageBuilder.toString().trim();
                                Exchange exchange = exchanges.get(currExchange);
                                if (exchange != null) {
                                    out.println("ok");
                                    exchange.publishMessage(routingKey, message);
                                } else {
                                    out.println("error");
                                }
                            }
                            break;

                        case "subscribe":
                            if (currQueue == null) {
                                out.println("error no queue declared");
                            } else {
                                BlockingQueue<String> queue = queues.get(currQueue);
                                if (queue != null) {
                                    out.println("ok");
                                    String message;
                                    while ((message = queue.take()) != null) {
                                        out.println(message);
                                    }
                                    String stopCommand;
                                    while ((stopCommand = in.readLine()) != null) {
                                        if (stopCommand.equals("stop")) {
                                            out.println("ok stopped");
                                            break;
                                        }
                                    }
                                } else {
                                    out.println("error");
                                }
                            }
                            break;

                        case "exit":
                            out.println("ok bye");
                            clientSocket.close();
                            return;

                        default:
                            out.println("error unknown command");
                            break;
                    }
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
