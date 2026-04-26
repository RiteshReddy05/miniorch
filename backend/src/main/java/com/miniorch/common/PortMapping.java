package com.miniorch.common;

public record PortMapping(int hostPort, int containerPort, String protocol) {

    public PortMapping {
        if (hostPort < 1 || hostPort > 65535) {
            throw new IllegalArgumentException("hostPort out of range: " + hostPort);
        }
        if (containerPort < 1 || containerPort > 65535) {
            throw new IllegalArgumentException("containerPort out of range: " + containerPort);
        }
        if (protocol == null || protocol.isBlank()) {
            protocol = "tcp";
        } else {
            protocol = protocol.toLowerCase();
        }
        if (!protocol.equals("tcp") && !protocol.equals("udp")) {
            throw new IllegalArgumentException("protocol must be tcp or udp: " + protocol);
        }
    }

    public static PortMapping tcp(int hostPort, int containerPort) {
        return new PortMapping(hostPort, containerPort, "tcp");
    }
}
