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
    private Runnable connectionLostListener;

    private Thread listenThread;
    private volatile boolean listening = false;

    public ClientNetworkAdapterImpl(String host, int port, Runnable connectionLostCallback) {
        this.host = host;
        this.port = port;
        this.connectionLostListener = connectionLostCallback;

        try {
            openSocket();
            startListenThread();
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            if (connectionLostListener != null) connectionLostListener.run();
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
            try { t.join(300); } catch (InterruptedException ignored) {
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

                if (obj instanceof NetPacket packet
                        && listener != null
                        && packet.type() != null) {
                    listener.onPacketReceived(packet);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (!listening) return;
            System.err.println("Connection closed: " + e.getMessage());
            if (connectionLostListener != null) connectionLostListener.run();
        }
    }

    @Override
    public void reconnect() throws IOException {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
            openSocket();
            startListenThread();
        }
    }

    @Override
    public void setConnectionLostListener(Runnable callback) {
        this.connectionLostListener = callback;
    }

    @Override
    public void disconnect() {
        synchronized (ioLock) {
            stopListenThread();
            disconnectInternal();
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

    @Override
    public void sendPacket(NetPacket packet){
        synchronized (ioLock) {
            try{
                if (socket == null || socket.isClosed() || out == null) {  throw new IOException("Socket is closed");  }
                        out.writeObject(packet);
                        out.flush();

            }catch (IOException e){

             }
        }

    }

    @Override
    public void setListener(PacketListener listener) {
        this.listener = listener;
    }
}
