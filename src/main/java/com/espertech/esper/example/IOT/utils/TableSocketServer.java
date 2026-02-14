package com.espertech.esper.example.IOT.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TableSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(TableSocketServer.class);
    private final int port;
    private final Set<PrintWriter> clients = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private boolean running = false;
    private ServerSocket serverSocket;
    private final CountDownLatch firstClientLatch = new CountDownLatch(1);

    public TableSocketServer(int port) {
        this.port = port;
    }

    public void start() {
        if (running)
            return;
        running = true;
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                serverSocket.setReuseAddress(true);
                logger.info("TableSocketServer started on port {}", port);
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("New client connected: {}", clientSocket.getRemoteSocketAddress());
                    executorService.execute(() -> handleClient(clientSocket));
                }
            } catch (java.net.BindException be) {
                logger.error("COULD NOT START TableSocketServer: Port {} is already in use. " +
                        "Please close any other instances or use 'netstat -ano | findstr :{}' to find the PID and kill it.",
                        port, port);
            } catch (Exception e) {
                if (running) {
                    logger.error("Error in TableSocketServer: ", e);
                }
            }
        });
    }

    private void handleClient(Socket socket) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            clients.add(out);
            firstClientLatch.countDown(); // Signal that at least one client is connected
            // Keep connection open until client disconnects or server stops
            while (running && !socket.isClosed()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            logger.debug("Client disconnected: {}", socket.getRemoteSocketAddress());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void broadcast(String message) {
        synchronized (clients) {
            clients.removeIf(PrintWriter::checkError);
            for (PrintWriter client : clients) {
                client.println(message);
                client.flush();
            }
        }
    }

    public void waitForFirstClient() {
        try {
            logger.info("Waiting for the first Python listener to connect...");
            firstClientLatch.await();
            logger.info("First client connected, proceeding.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for client", e);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing server socket: ", e);
        }
        executorService.shutdownNow();
    }
}
