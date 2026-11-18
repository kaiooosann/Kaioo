package com.kaioo.volume;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Descobre os IPs da central para mostrar ao usuário. */
final class NetUtil {
    private NetUtil() {
    }

    /** Lista "interface: ip" de todas as redes IPv4 ativas (Wi-Fi, hotspot, Wi-Fi Direct...). */
    static List<String> allIps() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        out.add(ni.getName() + ": " + a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static String firstIp() {
        List<String> ips = allIps();
        if (ips.isEmpty()) return "IP-DA-CENTRAL";
        // Prefere Wi-Fi / hotspot.
        for (String s : ips) {
            if (s.startsWith("wlan") || s.startsWith("ap") || s.startsWith("p2p")) {
                return s.substring(s.indexOf(": ") + 2);
            }
        }
        String s = ips.get(0);
        return s.substring(s.indexOf(": ") + 2);
    }
}
