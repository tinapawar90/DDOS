package com.ddos.system.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class IPService {

   private final Map<String, Integer> visitors = new HashMap<>();
private final Set<String> blockedIPs = new HashSet<>();
private final Map<String, Deque<Long>> ipRequestTimes = new HashMap<>();

private static final int REQUEST_LIMIT = 5;
private static final int TIME_WINDOW_MS = 3000; 
private static final int REQUEST_THRESHOLD = 50;

Map<String, GeoLocation> ipToLocation = new HashMap<>();
Map<String, Integer> locationHitCount = new HashMap<>();


public void registerIP(String ip) {
    if (isBlocked(ip)) return;

    long now = System.currentTimeMillis();
    ipRequestTimes.putIfAbsent(ip, new ArrayDeque<>());
    Deque<Long> timestamps = ipRequestTimes.get(ip);

    timestamps.addLast(now);
    while (!timestamps.isEmpty() && timestamps.peekFirst() < now - TIME_WINDOW_MS) {
        timestamps.pollFirst();
    }

    visitors.put(ip, visitors.getOrDefault(ip, 0) + 1);

    if (timestamps.size() > REQUEST_LIMIT || visitors.get(ip) > REQUEST_THRESHOLD) {
        blockedIPs.add(ip);
    }

    GeoLocation geo = getGeoLocation(ip);
if (geo == null) return;    

ipToLocation.put(ip, geo);

String key = Math.round(geo.lat * 1000) + "," + Math.round(geo.lon * 1000);
locationHitCount.put(key, locationHitCount.getOrDefault(key, 0) + 1);

if (locationHitCount.get(key) > 10) {
    for (Map.Entry<String, GeoLocation> entry : ipToLocation.entrySet()) {
        if (Math.round(entry.getValue().lat * 1000) == Math.round(geo.lat * 1000) &&
            Math.round(entry.getValue().lon * 1000) == Math.round(geo.lon * 1000)) {
            blockedIPs.add(entry.getKey());
        }
    }
}
}


    public List<Map<String, Object>> getAllIPs() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : visitors.entrySet()) {
            Map<String, Object> ipData = new HashMap<>();
            ipData.put("ip", entry.getKey());
            ipData.put("requestCount", entry.getValue());
            result.add(ipData);
        }
        return result;
    }

    public List<Map<String, Object>> getBlockedIPs() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ip : blockedIPs) {
            Map<String, Object> ipData = new HashMap<>();
            ipData.put("ip", ip);
            ipData.put("requestCount", visitors.getOrDefault(ip, 0));
            ipData.put("blocked", true);
            result.add(ipData);
        }
        return result;
    }

    public boolean isBlocked(String ip) {
        return blockedIPs.contains(ip);
    }

    // 🆕 Add this method here
    public void unblockIP(String ip) {
        blockedIPs.remove(ip);
        ipRequestTimes.remove(ip);
        visitors.remove(ip);
    }

    public static class GeoLocation {
        public double lat;
        public double lon;
    }

   public GeoLocation getGeoLocation(String ip) {
    try {
        URI uri = new URI("http://ip-api.com/json/" + ip);
        URL url = uri.toURL(); 

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.toString());

        GeoLocation geo = new GeoLocation();
        geo.lat = jsonNode.get("lat").asDouble();
        geo.lon = jsonNode.get("lon").asDouble();

        return geo;
    } catch (Exception e) {
        System.out.println("Failed to fetch geolocation for IP: " + ip + ", error: " + e.getMessage());
        return null;
    }
}

    
}
