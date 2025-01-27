package dslab.broker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class DNSConnector {

    private final String dnsHost;
    private final int dnsPort;

    public DNSConnector(String dnsHost, int dnsPort) {
        this.dnsHost = dnsHost;
        this.dnsPort = dnsPort;
    }

    public void register(String domain, String host, int port) {
        try (Socket dnsSocket = new Socket(dnsHost, dnsPort);
             PrintWriter out = new PrintWriter(dnsSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(dnsSocket.getInputStream()))) {

            String answer = in.readLine();
            if ("ok SDP".equals(answer)) {
                String registration = String.format("register %s %s:%d", domain, host, port);
                out.println(registration);
            }else{
                dnsSocket.close();
            }
        } catch (Exception ignored) {
        }
    }
}
