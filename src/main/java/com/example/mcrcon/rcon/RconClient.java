package com.example.mcrcon.rcon;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Simple RCON client implementation for Minecraft
 */
public class RconClient implements AutoCloseable {
    
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;
    
    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private final Random random = new Random();
    
    private final String host;
    private final int port;
    private int timeoutMs;
    private boolean connected = false;
    
    public RconClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Connect to the RCON server and authenticate
     */
    public void connect(String password, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(timeoutMs);
            
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            
            // Authenticate
            authenticate(password);
            connected = true;
            
        } catch (SocketTimeoutException e) {
            close();
            throw new IOException("RCON connection timeout", e);
        } catch (IOException e) {
            close();
            throw new IOException("Failed to connect to RCON server: " + e.getMessage(), e);
        }
    }
    
    /**
     * Send a command to the server
     */
    public String sendCommand(String command) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to RCON server");
        }
        
        try {
            int requestId = random.nextInt();
            
            // Send command packet
            sendPacket(requestId, SERVERDATA_EXECCOMMAND, command);
            
            // Read response
            RconPacket response = readPacket();
            
            if (response.getId() != requestId) {
                throw new IOException("Invalid response ID");
            }
            
            return response.getBody();
            
        } catch (IOException e) {
            throw new IOException("Failed to send RCON command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticate with the server
     */
    private void authenticate(String password) throws IOException {
        int requestId = random.nextInt(Integer.MAX_VALUE);
        
        // Send auth packet
        sendPacket(requestId, SERVERDATA_AUTH, password);
        
        // Read auth response
        RconPacket response = readPacket();
        
        // Check for authentication failure
        // Some servers return -1 for auth failure, others return a different ID
        if (response.getId() == -1) {
            throw new IOException("RCON authentication failed - server returned -1 (invalid password)");
        }
        
        // Verify the response type is correct
        if (response.getType() != SERVERDATA_AUTH_RESPONSE) {
            throw new IOException("RCON authentication failed - unexpected response type: " + response.getType() + " (expected: " + SERVERDATA_AUTH_RESPONSE + ")");
        }
        
        // For successful auth, the ID should match (but some servers may vary)
        if (response.getId() != requestId) {
            // Some servers return different IDs, but if we got here and it's not -1, it might be OK
            // Continue with authentication - this is common with some server implementations
        }
        
        // Some servers send an empty packet after auth - try to consume it
        try {
            socket.setSoTimeout(1000); // Short timeout for this read
            readPacket(); // Consume any additional packet
            // If we get here, there was an additional packet - this is normal for some servers
        } catch (Exception ignored) {
            // Timeout or other error reading additional packet - this is fine
        } finally {
            // Restore original timeout
            socket.setSoTimeout(timeoutMs);
        }
    }
    
    /**
     * Send an RCON packet
     */
    private void sendPacket(int id, int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        int packetSize = 4 + 4 + bodyBytes.length + 1 + 1; // id + type + body + null terminator + padding
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + packetSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(packetSize);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(bodyBytes);
        buffer.put((byte) 0); // null terminator
        buffer.put((byte) 0); // padding
        
        outputStream.write(buffer.array());
        outputStream.flush();
    }
    
    /**
     * Read an RCON packet
     */
    private RconPacket readPacket() throws IOException {
        // Read packet size
        byte[] sizeBytes = new byte[4];
        inputStream.readFully(sizeBytes);
        
        ByteBuffer sizeBuffer = ByteBuffer.wrap(sizeBytes);
        sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int packetSize = sizeBuffer.getInt();
        
        if (packetSize < 10 || packetSize > 4096) {
            throw new IOException("Invalid packet size: " + packetSize);
        }
        
        // Read packet data
        byte[] packetBytes = new byte[packetSize];
        inputStream.readFully(packetBytes);
        
        ByteBuffer packetBuffer = ByteBuffer.wrap(packetBytes);
        packetBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int id = packetBuffer.getInt();
        int type = packetBuffer.getInt();
        
        // Read body (excluding null terminators)
        byte[] bodyBytes = new byte[packetSize - 8 - 2];
        packetBuffer.get(bodyBytes);
        
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        
        return new RconPacket(id, type, body);
    }
    
    @Override
    public void close() throws IOException {
        connected = false;
        
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
        }
        
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {}
        }
        
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * RCON packet data structure
     */
    @Data
    @AllArgsConstructor
    private static class RconPacket {
        private final int id;
        private final int type;
        private final String body;
    }
}