package com.observability.logging.aspect;

import com.observability.logging.publisher.EventLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Map;

@Aspect
public class ObservabilityAspect {

    private final EventLogger eventLogger;

    public ObservabilityAspect(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    @Around("@annotation(com.observability.sdk.aspect.Traceable)")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {

        Method    method    = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Traceable traceable = method.getAnnotation(Traceable.class);

        String opName = traceable.name().isBlank()
            ? joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName()
            : traceable.name();

        if (traceable.logEntry()) {
            eventLogger.debug("→ " + opName,
                Map.of("class",  joinPoint.getTarget().getClass().getName(),
                       "method", method.getName()));
        }

        long start = System.currentTimeMillis();

        try {
            Object result  = joinPoint.proceed();
            long   elapsed = System.currentTimeMillis() - start;

            if (traceable.trackDuration()) {
                eventLogger.metric(opName, elapsed);
            }

            return result;

        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            eventLogger.error("✗ " + opName + " failed after " + elapsed + "ms", ex);
            throw ex;  // always rethrow — never swallow application exceptions
        }
    }
}