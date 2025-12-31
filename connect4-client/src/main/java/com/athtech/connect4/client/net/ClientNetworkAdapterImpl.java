package com.athtech.connect4.client.net;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private CLIController reconnectListener;

    private final String host;
    private final int port;

    private final Object ioLock = new Object();

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private PacketListener listener;

    private Thread listenThread;
    private volatile boolean listening = false;

    private enum State {CONNECTED, RECONNECTING, DEAD}
    private volatile State state = State.DEAD;

    // queue packets during reconnect
    private final Queue<NetPacket> pendingPackets = new LinkedList<>();

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
        // always create ObjectOutputStream first
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
            try {
                t.join(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
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
            System.err.println("\nConnection lost: " + e.getMessage());
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
                Thread.sleep(3_000); // small pause to let OS fully release socket
                synchronized (ioLock) {
                    openSocket();
                    startListenThread();
                    state = State.CONNECTED;

                    // send queued packets
                    while (!pendingPackets.isEmpty()) {
                        NetPacket pkt = pendingPackets.poll();
                        try {
                            out.writeObject(pkt);
                            out.flush();
                        } catch (IOException e) {
                            System.err.println("[Network] Failed to resend packet: " + pkt.type());
                        }
                    }

                    ioLock.notifyAll();
                    System.out.println("Reconnected successfully.");
                    if (reconnectListener != null) reconnectListener.onNetworkReconnected();
                    return;
                }
            } catch (Exception ignored) {
            }
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
                // queue packets for retry after reconnect
                pendingPackets.add(packet);
                System.err.println("[Network] Currently reconnecting. Packet queued: " + packet.type());
                return;
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
                pendingPackets.add(packet); // retry later
            }
        }
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }

    public void setReconnectListener(CLIController controller) {
        this.reconnectListener = controller;
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
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }
}
