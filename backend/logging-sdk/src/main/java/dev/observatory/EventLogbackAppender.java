package dev.observatory;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.*;

public class EventLogbackAppender extends AppenderBase<ILoggingEvent> {
  private final EventLogger logger;
  private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

  public EventLogbackAppender(EventLogger logger) {
    this.logger = logger;
  }

  protected void append(ILoggingEvent e) {
    if (active.get()
        || Thread.currentThread().getName().startsWith("observatory-sdk")
        || e.getLoggerName().startsWith("dev.observatory.EventLogger")
        || e.getLoggerName().startsWith("jdk.internal.net")) return;
    active.set(true);
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("logger", e.getLoggerName());
      payload.put("thread", e.getThreadName());
      if (e.getThrowableProxy() != null)
        payload.put(
            "exception",
            ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(e.getThrowableProxy())
                .substring(
                    0,
                    Math.min(
                        16000,
                        ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(
                                e.getThrowableProxy())
                            .length())));
      String msg = e.getFormattedMessage();
      logger.emit(
          e.getLevel().toString(),
          "APPLICATION_LOG",
          1,
          msg.substring(0, Math.min(4096, msg.length())),
          payload);
    } finally {
      active.remove();
    }
  }
}
