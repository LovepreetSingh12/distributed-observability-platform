package com.observability.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability.sdk")
public class LoggingSDKProperties {

    /** Set false to fully disable the SDK without removing the dependency. */
    private boolean enabled = true;

    /** Kafka topic to publish events to. */
    private String kafkaTopic = "observability-events";

    /**
     * Service name stamped on every event.
     * If not set, falls back to spring.application.name.
     */
    private String serviceName;

    private String serviceVersion = "unknown";

    /** Register the HTTP request logging filter automatically. */
    private boolean logRequests = true;

    /** Enable the @Traceable AOP aspect. */
    private boolean aopEnabled = true;

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public boolean isEnabled()                { return enabled; }
    public void    setEnabled(boolean v)      { this.enabled = v; }
    public String  getKafkaTopic()            { return kafkaTopic; }
    public void    setKafkaTopic(String v)    { this.kafkaTopic = v; }
    public String  getServiceName()           { return serviceName; }
    public void    setServiceName(String v)   { this.serviceName = v; }
    public String  getServiceVersion()        { return serviceVersion; }
    public void    setServiceVersion(String v){ this.serviceVersion = v; }
    public boolean isLogRequests()            { return logRequests; }
    public void    setLogRequests(boolean v)  { this.logRequests = v; }
    public boolean isAopEnabled()             { return aopEnabled; }
    public void    setAopEnabled(boolean v)   { this.aopEnabled = v; }
}