package com.athtech.connect4.client.net;

import com.athtech.connect4.client.cli.CLIController;
import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.ResyncRequest;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private final Object resyncLock = new Object();
    private volatile boolean resyncRequested = false;
    private volatile boolean resyncInProgress = false;
    private String pendingUsername;
    private String pendingRelogCode;

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
            tryExecuteResync();
        } catch (IOException e) {
            System.err.println("Initial connection failed: " + e.getMessage());
            handleConnectionLost();
        }
    }



    public void onResyncFinished() {
            resyncInProgress = false;
    }

    private void openSocket() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        in = new ObjectInputStream(socket.getInputStream());
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


    private void stopListenThread() {
        listening = false;
        Thread t = listenThread;
        if (t != null && t.isAlive() && t != Thread.currentThread()) {
            try { t.join(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        listenThread = null;
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

    private void reconnectLoop() {
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                Thread.sleep(500);

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
                    if (pendingUsername != null && pendingRelogCode !=null){
                        resyncRequested = true;
                        tryExecuteResync();
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

    private void startListenThread() {
        listening = true;
        listenThread = new Thread(this::listenLoop, "ClientNetworkAdapter-ListenThread");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void requestResync(String username, String relogCode) {
        synchronized (resyncLock) {
            pendingUsername = username;
            pendingRelogCode = relogCode;
            resyncRequested = true;
        }
        tryExecuteResync();
    }

    private void tryExecuteResync() {
        synchronized (resyncLock) {
            if (!resyncRequested) return;
            if (resyncInProgress) return;
            if (netState != NetState.CONNECTED) return;
            if (out == null || socket == null || socket.isClosed()) return;

            resyncRequested = false;
            resyncInProgress = true;
        }

        sendPacket(new NetPacket(
                PacketType.RESYNC_REQUEST,
                pendingUsername,
                new ResyncRequest(pendingUsername, pendingRelogCode)
        ));
    }




    @Override
    public void updateCredentials(String username, String relogCode) {
        synchronized (resyncLock) {
            this.pendingUsername = username;
            this.pendingRelogCode = relogCode;
        }
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

    @Override
    public void disconnect() {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
            netState = NetState.DEAD;
            ioLock.notifyAll();
        }
    }


}
