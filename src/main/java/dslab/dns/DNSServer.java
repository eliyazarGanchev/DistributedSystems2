package dslab.dns;

import dslab.ComponentFactory;
import dslab.config.DNSServerConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;

public class DNSServer implements IDNSServer {

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
    private final int port;
    private ServerSocket serverSocket;
    private final ThreadFactory threadFactory;
    private boolean flag = true;


    public DNSServer(DNSServerConfig config) {
        this.port = config.port();
        this.threadFactory = new MyThreadFactory();

    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (flag) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadFactory.newThread(new DNSResolver(clientSocket)).start();
                } catch (SocketException e) {
                    if (flag) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        } finally {
            shutdown();
        }
    }

    @Override
    public void shutdown() {
        flag = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ComponentFactory.createDNSServer(args[0]).run();
    }

    private static class MyThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }
    }

    private class DNSResolver implements Runnable {
        private final Socket clientSocket;

        public DNSResolver(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("ok SDP");

                String input;
                while ((input = in.readLine()) != null) {
                    String[] commands = input.split(" ");
                    String command = commands[0];

                    switch (command) {
                        case "register":
                            if (commands.length == 3) {
                                String domain = commands[1];
                                String ipPort = commands[2];
                                map.put(domain, ipPort);
                                out.println("ok");
                            } else {
                                out.println("error usage: register <name> <ip:port>");
                            }
                            break;
                        case "unregister":
                            if (commands.length == 2) {
                                String dom = commands[1];
                                map.remove(dom);
                                out.println("ok");
                            } else {
                                out.println("error usage: unregister <name>");
                            }
                            break;
                        case "resolve":
                            if (commands.length == 2) {
                                String dom = commands[1];
                                String ipPort = map.get(dom);
                                if (ipPort != null) {
                                    out.println(ipPort);
                                } else {
                                    out.println("error domain not found");
                                }
                            } else {
                                out.println("error usage: resolve <name>");
                            }
                            break;
                        case "exit":
                            out.println("ok bye");
                            clientSocket.close();
                            return;
                        default:
                            out.println("error");
                            break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
