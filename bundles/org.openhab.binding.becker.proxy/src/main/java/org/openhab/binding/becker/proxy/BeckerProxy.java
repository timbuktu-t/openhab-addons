
package org.openhab.binding.becker.proxy;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.logging.LogManager;

import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// to filter periodic log queries use: .*(systemd.log_top_event_id_read|systemd.log_entry_read).*

// TODO (3) improve code quality, add javadoc, package-info and null annotations

public final class BeckerProxy {

    private static final String LOGGING_PROPERTIES = "logging.properties";

    private static final Logger logger = LoggerFactory.getLogger(BeckerProxy.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.format("usage: %s <local-port> <target-host> [<filter>]", BeckerProxy.class.getName());
            System.exit(0);
        }
        int port = Integer.parseInt(args[0]);
        InetAddress host = InetAddress.getByName(args[1]);
        String filter = args.length == 3 ? args[2] : "";
        try (InputStream is = BeckerProxy.class.getClassLoader().getResourceAsStream(LOGGING_PROPERTIES)) {
            if (is == null) {
                throw new FileNotFoundException(LOGGING_PROPERTIES);
            }
            LogManager.getLogManager().readConfiguration(is);
        }
        Server server = new Server(port);
        server.setHandler(contextHandler(host, filter));
        server.start();
        System.out.println(">>> PROXY STARTED; ENTER COMMENT OR LEAVE EMPTY TO STOP");
        try (Scanner in = new Scanner(System.in)) {
            while (true) {
                String line = in.nextLine();
                if (line.isEmpty()) {
                    break;
                }
                logger.info("[COMMENT] {}", line);
            }
        }
        server.stop();
        server.join();
    }

    private static ServletContextHandler contextHandler(InetAddress host, String filter) {
        ServletHolder socket = new ServletHolder(BeckerSocketProxy.class);
        socket.setInitParameter(BeckerSocketProxy.PROXYTO, "ws://" + host.getHostName() + "/jrpc");
        socket.setInitParameter(BeckerSocketProxy.FILTER, filter);
        ServletHolder proxy = new ServletHolder(ProxyServlet.Transparent.class);
        proxy.setInitParameter("proxyTo", "http://" + host.getHostName() + "/");
        proxy.setInitParameter("prefix", "/");
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(socket, "/jrpc");
        context.addServlet(proxy, "/");
        return context;
    }
}
