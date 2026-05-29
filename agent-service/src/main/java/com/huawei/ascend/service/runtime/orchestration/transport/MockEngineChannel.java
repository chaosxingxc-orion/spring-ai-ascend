package com.huawei.ascend.service.runtime.orchestration.transport;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Mock transport channel: Java-serializes {@link ExecuteRequest} and {@link AgentEvent} to bytes
 * and back, proving the neutral wire shapes are serializable WITHOUT a real RPC server. The
 * round-trip is in-JVM; a real transport would put these bytes on a socket.
 */
public final class MockEngineChannel {

    public byte[] serialize(Object o) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(o);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("mock wire serialize failed: " + e.getMessage(), e);
        }
    }

    public ExecuteRequest deserializeRequest(byte[] wire) {
        return (ExecuteRequest) deserialize(wire);
    }

    public AgentEvent deserializeEvent(byte[] wire) {
        return (AgentEvent) deserialize(wire);
    }

    private Object deserialize(byte[] wire) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(wire))) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("mock wire deserialize failed: " + e.getMessage(), e);
        }
    }
}
