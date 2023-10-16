package com.tufitko.kafkafsjca;

import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SslEngineFactory;
import org.apache.kafka.common.security.ssl.DefaultSslEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;
import java.util.Set;

public class AutoReloadSSLEngineFactory implements SslEngineFactory {

    public static final String MONITOR_SSL_FACTORY_REFRESH_INTERVAL_SECONDS = "tufitko.kafkafsjca.ssl_factory.monitor.refresh.interval.seconds";
    private static final Logger log = LoggerFactory.getLogger(AutoReloadSSLEngineFactory.class);
    int monitorRefreshIntervalSeconds = 60;
    String keystorePath = null;
    String truststorePath = null;
    private Map<String, ?> configs;
    private volatile long keystoreLastModifiedTime;
    private volatile long truststoreLastModifiedTime;
    private volatile DefaultSslEngineFactory sslEngineFactory;
    private Thread monitoringThread;

    @Override
    public void configure(Map<String, ?> configs) {
        if (configs.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG) != null) {
            this.keystorePath = findFileOrThrow(configs, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG);
        } else {
            this.keystorePath = null;
        }
        if (configs.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG) != null) {
            this.truststorePath = findFileOrThrow(configs, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG);
        } else {
            this.truststorePath = null;
        }
        if (configs.get(MONITOR_SSL_FACTORY_REFRESH_INTERVAL_SECONDS) != null) {
            monitorRefreshIntervalSeconds = Integer.parseInt((String) configs.get(MONITOR_SSL_FACTORY_REFRESH_INTERVAL_SECONDS));
        }
        this.configs = configs;
        this.sslEngineFactory = createDefaultSslEngineFactory(configs);
        if (monitoringThread == null || !monitoringThread.isInterrupted()) {
            startMonitoringThread();
        }
    }

    @Override
    public SSLEngine createClientSslEngine(String host, int port, String endpointIdentificationAlgorithm) {
        return sslEngineFactory.createClientSslEngine(host, port, endpointIdentificationAlgorithm);
    }

    @Override
    public SSLEngine createServerSslEngine(String host, int port) {
        return sslEngineFactory.createServerSslEngine(host, port);
    }

    @Override
    public boolean shouldBeRebuilt(Map<String, Object> configs) {
        return sslEngineFactory.shouldBeRebuilt(configs);
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return sslEngineFactory.reconfigurableConfigs();
    }

    @Override
    public KeyStore keystore() {
        return sslEngineFactory.keystore();
    }

    @Override
    public KeyStore truststore() {
        return sslEngineFactory.truststore();
    }

    @Override
    public void close() {
        sslEngineFactory.close();
        if (monitoringThread != null) {
            monitoringThread.interrupt();
        }
    }

    private DefaultSslEngineFactory createDefaultSslEngineFactory(Map<String, ?> configs) {
        DefaultSslEngineFactory newFactory = new DefaultSslEngineFactory();
        newFactory.configure(configs);
        return newFactory;
    }

    private void startMonitoringThread() {
        monitoringThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    log.debug("reread tls files");
                    boolean needReconfigure = false;
                    if (keystorePath != null) {
                        long tmpKeystoreLastModifiedTime = getFileLastModifiedTime(keystorePath);
                        if (keystoreLastModifiedTime != tmpKeystoreLastModifiedTime) {
                            needReconfigure = true;
                            keystoreLastModifiedTime = tmpKeystoreLastModifiedTime;
                        }
                    }
                    if (truststorePath != null) {
                        needReconfigure = needReconfigure || (truststoreLastModifiedTime != getFileLastModifiedTime(truststorePath));
                        long tmpTruststoreLastModifiedTime = getFileLastModifiedTime(truststorePath);
                        if (truststoreLastModifiedTime != tmpTruststoreLastModifiedTime) {
                            needReconfigure = true;
                            truststoreLastModifiedTime = tmpTruststoreLastModifiedTime;
                        }
                    }
                    if (needReconfigure) {
                        log.debug("reload tls files");
                        sslEngineFactory = createDefaultSslEngineFactory(configs);
                    }

                    Thread.sleep(monitorRefreshIntervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        monitoringThread.setName(AutoReloadSSLEngineFactory.class + "Monitor");
        monitoringThread.setDaemon(true);
        monitoringThread.start();
    }

    private long getFileLastModifiedTime(String path) {
        try {
            return Files.getLastModifiedTime(Paths.get(path)).toMillis();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not read tls file '%s'", path), e);
        }
    }


    private String findFileOrThrow(Map<String, ?> configs, String key) {
        String path = (String) configs.get(key);
        if (isAbsolutePathThatExists(path)) {
            log.debug("Verified file exists: {}", path);
            return path;
        }
        throw new RuntimeException(new FileNotFoundException(path + " does not exist. Check the setting: " + key));
    }

    private boolean isAbsolutePathThatExists(String path) {
        File file = new File(path);
        return file.isAbsolute() && file.exists() && !file.isDirectory();
    }

}
