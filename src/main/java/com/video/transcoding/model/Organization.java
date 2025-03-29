package com.video.transcoding.model;

public enum Organization {
    UNIT_1("192.168.205.108"),
    UNIT_2("192.168.205.104");

    private final String ip;

    Organization(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public static Organization fromIp(String ip) {
        for (Organization org : values()) {
            if (org.getIp().equals(ip)) {
                return org;
            }
        }
        throw new IllegalArgumentException("Unknown organization IP: " + ip);
    }
} 