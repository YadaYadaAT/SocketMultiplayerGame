package com.athtech.gomoku.client.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.ResyncRequest;

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

    private volatile PacketListener listener;
    private volatile ConnectionNotificationListener conNotifier;
    private volatile SyncAndConInputBlockerInter syncAndConInputBlockerInter;
    private volatile SyncAndConInputUnblockerInter syncAndConInputUnblockerInter;

    private Thread listenThread;
    private volatile boolean listening = false;

    private volatile NetState netState = NetState.DEAD;
    private Thread reconnectSpinner;



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
            //todo ..resync on the spot?...
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

            enableSyncAndConInputBlocker();
            System.err.println("\nConnection lost: " + e.getMessage());
            sendToConNotifier("\uD83D\uDD0C Connection lost: " + e.getMessage());
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


        startReconnectSpinner();
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

        while (true) {
            try {
                Thread.sleep(1000);

                synchronized (ioLock) {
                    openSocket();
                    startListenThread();
                    netState = NetState.CONNECTED;
                    stopReconnectSpinnerSuccess();
                    System.out.println("Reconnected successfully.");

                    if (pendingUsername != null && pendingRelogCode != null) {
                        tryExecuteResync();
                    }else{
                        disableSyncAndConInputBlocker();
                    }

                    ioLock.notifyAll();
                    return;
                }
            } catch (Exception ignored) {
                // just keep looping
            }
        }

    }

    private void startListenThread() {
        listening = true;
        listenThread = new Thread(this::listenLoop, "ClientNetworkAdapter-ListenThread");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void requestResync() {
        tryExecuteResync();
    }

    private void tryExecuteResync() {
        synchronized (resyncLock) {
            if (resyncInProgress) {
                disableSyncAndConInputBlocker();
                return;
            }
            if (netState != NetState.CONNECTED) {
                disableSyncAndConInputBlocker();
                return;
            }
            if (out == null || socket == null || socket.isClosed()) {
                disableSyncAndConInputBlocker();
                return;
            }

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


    private void startReconnectSpinner() {
        reconnectSpinner = new Thread(() -> {
            char[] spinner = {'|', '/', '-', '\\'};
            int i = 0;

            System.out.print("🔄 Reconnecting ");
            sendToConNotifier("🔄 Reconnecting ");
            while (netState == NetState.RECONNECTING) {
                System.out.print("\r 🔄 Reconnecting  " + spinner[i++ % spinner.length]);
                sendToConNotifier("🔄 Reconnecting  " + spinner[i++ % spinner.length]);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "Reconnect-Spinner");

        reconnectSpinner.setDaemon(true);
        reconnectSpinner.start();
    }

    private void stopReconnectSpinnerSuccess() {
        if (reconnectSpinner != null) {
            reconnectSpinner.interrupt();
        }
        System.out.print("\rConnected ✓                     \n");
        sendToConNotifier("\uD83C\uDF10 Connected");
    }

    @Override
    public void setConNotifier(ConnectionNotificationListener conNotifier) {
        this.conNotifier = conNotifier;
    }

    @Override
    public void setSyncAndConInputBlocker( SyncAndConInputBlockerInter sib) {
        this.syncAndConInputBlockerInter = sib;
    }

    @Override
    public void setSyncAndConInputUnblocker( SyncAndConInputUnblockerInter siu) {
        this.syncAndConInputUnblockerInter = siu;
    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
        if (netState == NetState.CONNECTED){
            sendToConNotifier("\uD83C\uDF10 Connected");
        } else if (netState == NetState.RECONNECTING) {
            sendToConNotifier("Reconnecting");
        }else{
            sendToConNotifier("\uD83D\uDD0C Connection is dead");
        }

    }




    private void sendToConNotifier(String msg){
        if (conNotifier != null){
            conNotifier.connectionNotifer(msg);
        }
    }


    public void enableSyncAndConInputBlocker() {
        if (syncAndConInputBlockerInter !=null){
            syncAndConInputBlockerInter.syncAndConInputBlocker();
        }
    }

    public void disableSyncAndConInputBlocker() {
        if (syncAndConInputUnblockerInter !=null){
            syncAndConInputUnblockerInter.syncAndConInputUnblocker();
        }
    }

}
