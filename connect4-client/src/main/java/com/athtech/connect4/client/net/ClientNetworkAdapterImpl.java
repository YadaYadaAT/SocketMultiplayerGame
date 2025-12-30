package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private final String host;
    private final int port;

    private final Object ioLock = new Object();

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private PacketListener listener;

    private Thread listenThread;
    private volatile boolean listening = false;

    // Network state
    private enum State { CONNECTED, RECONNECTING, DEAD }
    private volatile State state = State.DEAD;

    public ClientNetworkAdapterImpl(String host, int port) {
        this.host = host;
        this.port = port;
        attemptInitialConnection();
    }

    private void attemptInitialConnection() {
        try {
            openSocket();
            startListenThread();
            state = State.CONNECTED;
        } catch (IOException e) {
            System.err.println("Initial connection failed: " + e.getMessage());
            handleConnectionLost();
        }
    }

    private void openSocket() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    private void startListenThread() {
        listening = true;
        listenThread = new Thread(this::listenLoop, "ClientNetworkAdapter-ListenThread");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    private void stopListenThread() {
        listening = false;
        Thread t = listenThread;
        if (t != null && t.isAlive() && t != Thread.currentThread()) {
            try { t.join(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        listenThread = null;
    }

    private void listenLoop() {
        try {
            while (listening) {
                Object obj = in.readObject();
                if (!listening) break;

                if (obj instanceof NetPacket packet && listener != null && packet.type() != null) {
                    listener.onPacketReceived(packet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (!listening) return;
            System.err.println("\n Connection lost: " + e.getMessage());
            handleConnectionLost();
        }
    }

    private void handleConnectionLost() {
        synchronized (ioLock) {
            if (state == State.RECONNECTING) return;
            state = State.RECONNECTING;
            stopListenThread();
            disconnectInternal();
        }

        new Thread(this::reconnectLoop, "ClientNetworkAdapter-Reconnect").start();
    }

    private void reconnectLoop() {
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;
        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                Thread.sleep(5_000);
                synchronized (ioLock) {
                    openSocket();
                    startListenThread();
                    state = State.CONNECTED;
                    ioLock.notifyAll();
                    System.out.println("Reconnected successfully.");
                    return;
                }
            } catch (Exception ignored) {}
        }

        synchronized (ioLock) {
            state = State.DEAD;
            ioLock.notifyAll();
        }
        System.err.println("Failed to reconnect after " + MAX_ATTEMPTS + " attempts.");
    }

    @Override
    public void sendPacket(NetPacket packet) {
        synchronized (ioLock) {
            if (state == State.RECONNECTING) {
                System.err.println("[Network] Currently reconnecting. Packet dropped or queued: " + packet.type());
                return; // drop or optionally queue for later
            }

            if (state != State.CONNECTED) {
                System.err.println("[Network] Cannot send packet, network is down: " + packet.type());
                return;
            }

            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                System.err.println("[Network] Send failed, marking connection lost: " + e.getMessage());
                handleConnectionLost();
            }
        }
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }

    @Override
    public void disconnect() {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
            state = State.DEAD;
            ioLock.notifyAll();
        }
    }

    private void disconnectInternal() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}

        in = null;
        out = null;
        socket = null;
    }
}
