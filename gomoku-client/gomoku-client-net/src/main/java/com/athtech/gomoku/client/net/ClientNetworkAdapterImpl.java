package com.athtech.gomoku.client.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.ResyncRequest;

import java.io.*;
import java.net.Socket;

public class ClientNetworkAdapterImpl implements ClientNetworkAdapter {

    private final Object resyncLock = new Object(); // private mutex protecting I/O / connection state
    private volatile boolean resyncRequested = false; // resync is triggered after delayed server response or after disconnects to ensure that available server state is up to date
    private volatile boolean resyncInProgress = false;
    private String pendingUsername;
    private String pendingRelogCode;

    private final String host;
    private final int port;

    private final Object ioLock = new Object();  // private mutex protecting I/O / connection state

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
            openSocket(); // initialize sockets
            startListenThread(); // initialize the listening loop
            netState = NetState.CONNECTED;
        } catch (IOException e) { // handle FIN sent / connection lost
            System.err.println("Initial connection failed: " + e.getMessage());
            handleConnectionLost();
        }
    }

    // indicate resync process end
    public void onResyncFinished() {
            resyncInProgress = false;
    }

    // initialize client socket
    private void openSocket() throws IOException {
        socket = new Socket(host, port); // if a connection is not found, method exits with exception
        out = new ObjectOutputStream(socket.getOutputStream()); // wraps socket output stream and handles serialization.
        out.flush(); // declared before input stream to avoid deadlocks
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        in = new ObjectInputStream(socket.getInputStream()); // wraps socket input stream and handles deserialization
    }

    // Creates a thread that accepts NetPackets
    private void startListenThread() {
        listening = true;
        listenThread = new Thread(this::listenLoop, "ClientNetworkAdapter-ListenThread"); // run the listenLoop
        listenThread.setDaemon(true); // app does not wait for the thread to terminate in order to close
        listenThread.start();
    }

    //listenLoop accepts net packets and forwards them to the cli/gui controllers
    private void listenLoop() {
        try {
            while (listening) { // check if a cli/gui listener is hooked
                Object obj = in.readObject();
                if (!listening) break;
                if (obj instanceof NetPacket packet && listener != null && packet.type() != null) { // validate received packet
                    listener.onPacketReceived(packet); // listener could either be cli or gui controller (also scalable with other uis)
                }
            }
        } catch (IOException | ClassNotFoundException e) { // triggers if connection is lost due to closed socket || ObjectInputStream fails to deserialize
            if (!listening) return;

            enableSyncAndConInputBlocker();
            System.err.println("\nConnection lost: " + e.getMessage());
            sendToConNotifier("\uD83D\uDD0C Connection lost: " + e.getMessage());
            handleConnectionLost(); // attempt to reconnect
        } catch (Exception e){
            // used for debugging: grab corrupted packets
        }
    }

    // triggered in case of disconnection
    private void handleConnectionLost() {
        synchronized (ioLock) { // synchronized ensures only one thread handles connection loss at a time
            if (netState == NetState.RECONNECTING) return;
            netState = NetState.RECONNECTING;

            stopListenThread(); // first we stop the listener
            disconnectInternal(); // then we can safely disconnect the active sockets
        }

        startReconnectSpinner(); // start the spinner for visual output
        new Thread(this::reconnectLoop, "ClientNetworkAdapter-Reconnect").start(); // attempt to reconnect
    }


    // stops the listener and sets the thread to null to allow for new initialization
    private void stopListenThread() {
        listening = false;
        Thread t = listenThread;
        if (t != null && t.isAlive() && t != Thread.currentThread()) {
            try { t.join(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        listenThread = null;
    }

    // safely shut down i/o socket to allow for re-initialization
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

    // attempt to reconnect client to server
    private void reconnectLoop() {

        while (true) {
            try {
                Thread.sleep(1000); // let the reconnect loop run only every second

                synchronized (ioLock) { // enter critical section
                    // open new socket - we do this first because this ensures that a connection is found. if a connection is not found, method exits with exception
                    openSocket();
                    startListenThread(); // start a new listener
                    netState = NetState.CONNECTED;
                    stopReconnectSpinnerSuccess(); // stop the spinner
                    System.out.println("Reconnected successfully.");

                    if (pendingUsername != null && pendingRelogCode != null) { // if user was logged in with an active relog code before connection was lost, we attempt to restore the session
                        tryExecuteResync();
                    }else{
                        disableSyncAndConInputBlocker(); // allow user to perform actions again
                    }

                    ioLock.notifyAll(); // wakes all awaiting threads
                    return;
                }
            } catch (Exception ignored) {
                // just keep looping
            }
        }

    }

    // LEGACY - called by send thread
    public void requestResync() {
        tryExecuteResync();
    }

    // attempts to send resync request to server
    private void tryExecuteResync() {
        synchronized (resyncLock) { // enter critical section
            if (resyncInProgress) {
                disableSyncAndConInputBlocker(); // disallow user to perform actions if resync is in progress
                return;
            }
            if (netState != NetState.CONNECTED) {
                disableSyncAndConInputBlocker(); // disallow user to perform actions if connection does not exist
                return;
            }
            if (out == null || socket == null || socket.isClosed()) {
                disableSyncAndConInputBlocker(); // disallow user to perform actions if socket does not exist
                return;
            }

            resyncRequested = false;
            resyncInProgress = true;
        }

        // Send resync request packet to server with client username and relog code
        sendPacket(new NetPacket(
                PacketType.RESYNC_REQUEST,
                pendingUsername,
                new ResyncRequest(pendingUsername, pendingRelogCode)
        ));
    }


    // locally update user credentials
    @Override
    public void updateCredentials(String username, String relogCode) {
        synchronized (resyncLock) {
            this.pendingUsername = username;
            this.pendingRelogCode = relogCode;
        }
    }

    // handles all packets to be sent to server
    @Override
    public void sendPacket(NetPacket packet) {
        synchronized (ioLock) { // enter critical section
            if (netState != NetState.CONNECTED) {
                System.err.println("[Network] Cannot send packet, network is down: ");
                return;
            }
            if (out == null || socket == null || socket.isClosed()) {
                return;
            }
            if (netState == NetState.RECONNECTING) {
                System.err.println("[Network] Currently reconnecting.");
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

    // Get network state
    @Override
    public NetState getState() {
        return netState;
    }

    // Triggered on app exit
    @Override
    public void disconnect() {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
            netState = NetState.DEAD;
            ioLock.notifyAll();
        }
    }

//                                                         //
//          -- UI CALLBACK RELATED METHODS --              //
//                                                         //

    //Spinner runs on its own thread while we await reconnection
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

    // Stops the spinner
    private void stopReconnectSpinnerSuccess() {
        if (reconnectSpinner != null) {
            reconnectSpinner.interrupt();
        }
        System.out.print("\rConnected ✓                     \n");
        sendToConNotifier("\uD83C\uDF10 Connected");
    }

    // Hooks callback method to javafx / notification handler
    @Override
    public void setConNotifier(ConnectionNotificationListener conNotifier) {
        this.conNotifier = conNotifier;
    }

    // Sets the method to be called to handle input blocks
    @Override
    public void setSyncAndConInputBlocker( SyncAndConInputBlockerInter sib) {
        this.syncAndConInputBlockerInter = sib;
    }

    // Sets the method to be called to handle input unblocking
    @Override
    public void setSyncAndConInputUnblocker( SyncAndConInputUnblockerInter siu) {
        this.syncAndConInputUnblockerInter = siu;
    }

    // Sets listener method to be used in listen loop - called by main thread
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

    // Populate UI regarding connection status
    private void sendToConNotifier(String msg){
        if (conNotifier != null){
            conNotifier.connectionNotifer(msg);
        }
    }

    // Enables sync & input (unblock)
    public void enableSyncAndConInputBlocker() {
        if (syncAndConInputBlockerInter !=null){
            syncAndConInputBlockerInter.syncAndConInputBlocker();
        }
    }

    // Disables sync & input (block)
    public void disableSyncAndConInputBlocker() {
        if (syncAndConInputUnblockerInter !=null){
            syncAndConInputUnblockerInter.syncAndConInputUnblocker();
        }
    }

}
