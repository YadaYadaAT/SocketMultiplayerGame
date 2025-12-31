package com.athtech.connect4.client.net;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;

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
            if (state == State.RECONNECTING) return;
            state = State.RECONNECTING;
            stopListenThread();
            drainInputQuietly();
            disconnectInternal();
        }

        new Thread(this::reconnectLoop, "ClientNetworkAdapter-Reconnect").start();
    }

    private void drainInputQuietly() {
        if (socket == null) return;
        try {
            socket.setSoTimeout(1);
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[1024];
            while (is.read(buf) != -1) {
                // discard
            }
            out.reset();
        } catch (Exception ignored) {}
    }

    private void reconnectLoop() {
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                Thread.sleep(6000);

                synchronized (ioLock) {
                    openSocket();
                    startListenThread();
                    state = State.CONNECTED;
                    ioLock.notifyAll();
                    System.out.println("Reconnected successfully.");
                    if (reconnectListener != null) reconnectListener.onNetworkReconnected();
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
                System.err.println("[Network] Currently reconnecting.");
                return;
            }
            if (state != State.CONNECTED) {
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
