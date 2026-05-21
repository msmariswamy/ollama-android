package com.ollama.mobile.network;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalNetworkScanner {

    private static final int OLLAMA_PORT = 11434;
    private static final int CONNECT_TIMEOUT_MS = 300;

    public interface ScanCallback {
        void onProgress(int scanned, int total);
        void onHostFound(String ip);
        void onComplete(List<String> found);
    }

    public void scan(Context context, ScanCallback callback) {
        new Thread(() -> {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            if (ipInt == 0) {
                callback.onComplete(new ArrayList<>());
                return;
            }

            // Build subnet prefix (e.g. 192.168.1.)
            String myIp = String.format("%d.%d.%d.%d",
                    (ipInt & 0xff),
                    (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff),
                    (ipInt >> 24 & 0xff));
            String[] parts = myIp.split("\\.");
            String subnet = parts[0] + "." + parts[1] + "." + parts[2] + ".";

            int total = 254;
            AtomicInteger scanned = new AtomicInteger(0);
            List<String> found = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(50);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 1; i <= total; i++) {
                final String host = subnet + i;
                Future<?> f = pool.submit(() -> {
                    if (isOllamaReachable(host)) {
                        synchronized (found) {
                            found.add(host);
                        }
                        callback.onHostFound(host);
                    }
                    int done = scanned.incrementAndGet();
                    callback.onProgress(done, total);
                });
                futures.add(f);
            }

            pool.shutdown();
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            callback.onComplete(new ArrayList<>(found));
        }).start();
    }

    private boolean isOllamaReachable(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, OLLAMA_PORT), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
