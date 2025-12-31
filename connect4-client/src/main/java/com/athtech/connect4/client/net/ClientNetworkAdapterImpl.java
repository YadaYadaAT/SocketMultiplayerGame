package com.athtech.connect4.client.net;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;

import java.io.*;
import java.net.Socket;

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

    private volatile NetState netState = NetState.DEAD;

    public ClientNetworkAdapterImpl(String host, int port) {
        this.host = host;
        this.port = port;
        attemptInitialConnection();
    }

    private void attemptInitialConnection() {
        try {
            openSocket();
            startListenThread();
            netState = NetState.CONNECTED;
        } catch (IOException e) {
            System.err.println("Initial connection failed: " + e.getMessage());
            handleConnectionLost();
        }
    }

    private void openSocket() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        in = new ObjectInputStream(socket.getInputStream());
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        out.writeObject(new NetPacket(PacketType.HANDSHAKE,"user","handshake"));
        out.flush();
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
            System.err.println("\nConnection lost: " + e.getMessage());
            handleConnectionLost();
        } catch (Exception e){
            //silence weirdo objectStream syncs to port header i guess... grab them here
        }
    }

    private void handleConnectionLost() {
        synchronized (ioLock) {
            if (netState == NetState.RECONNECTING) return;
            netState = NetState.RECONNECTING;
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
                Thread.sleep(3000);

                synchronized (ioLock) {
                    if(netState == NetState.CONNECTED) {
                        ioLock.notifyAll();
                        return;
                    }
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    openSocket();
                    startListenThread();
                    netState = NetState.CONNECTED;
                    System.out.println("Reconnected successfully.");
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    if (reconnectListener != null){

                        reconnectListener.onNetworkReconnected();
                    }
                    ioLock.notifyAll();
                    return;
                }

            } catch (Exception ignored) {}
        }

        synchronized (ioLock) {
            netState = NetState.DEAD;
            ioLock.notifyAll();
        }
        System.err.println("Failed to reconnect after " + MAX_ATTEMPTS + " attempts.");
    }

    @Override
    public void sendPacket(NetPacket packet) {
        synchronized (ioLock) {
            if (netState != NetState.CONNECTED || out == null || socket == null || socket.isClosed()) {
                return;
            }
            if (netState == NetState.RECONNECTING) {
                System.err.println("[Network] Currently reconnecting.");
                return;
            }
            if (netState != NetState.CONNECTED) {
                System.err.println("[Network] Cannot send packet, network is down: ");
                return;
            }
            try {
                out.writeObject(packet);
                out.flush();
            } catch (IOException e) {
                System.err.println("[Network] Send failed, marking connection lost: ");
                handleConnectionLost();
            }
        }
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }

    @Override
    public NetState getState() {
        return netState;
    }

    public void setReconnectListener(CLIController controller) {
        this.reconnectListener = controller;
    }

    @Override
    public void disconnect() {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
            netState = NetState.DEAD;
            ioLock.notifyAll();
        }
    }

    private void disconnectInternal() {
        try {
            if (socket != null) {
                try { socket.shutdownInput(); } catch (Exception ignored) {}
                try { socket.shutdownOutput(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}

        in = null;
        out = null;
        socket = null;
    }
}
