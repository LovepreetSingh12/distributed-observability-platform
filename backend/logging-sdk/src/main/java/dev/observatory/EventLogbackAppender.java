package dev.observatory;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.util.HashMap;
import java.util.Map;

public class EventLogbackAppender extends AppenderBase<ILoggingEvent> {

    private final EventLogger logger;

    private final ThreadLocal<Boolean> active =
            ThreadLocal.withInitial(() -> false);

    public EventLogbackAppender(EventLogger logger) {
        this.logger = logger;
    }

    @Override
    protected void append(ILoggingEvent e) {

        if (active.get()
                || Thread.currentThread().getName().startsWith("observatory-sdk")
                || e.getLoggerName().startsWith("dev.observatory.EventLogger")
                || e.getLoggerName().startsWith("jdk.internal.net")) {
            return;
        }

        active.set(true);

        try {
            Map<String, Object> payload = new HashMap<>();

            payload.put("logger", e.getLoggerName());
            payload.put("thread", e.getThreadName());

            if (e.getThrowableProxy() != null) {

                String exception = ThrowableProxyUtil.asString(
                        e.getThrowableProxy()
                );

                payload.put(
                        "exception",
                        exception.substring(
                                0,
                                Math.min(16000, exception.length())
                        )
                );
            }

            String msg = e.getFormattedMessage();

            logger.emit(
                    e.getLevel().toString(),
                    "APPLICATION_LOG",
                    1,
                    msg.substring(
                            0,
                            Math.min(4096, msg.length())
                    ),
                    payload
            );

        } finally {
            active.remove();
        }
    }
}