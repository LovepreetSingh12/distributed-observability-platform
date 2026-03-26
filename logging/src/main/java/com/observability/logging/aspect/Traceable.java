package com.observability.logging.aspect;

import java.lang.annotation.*;

/**
 * Marks a method for automatic observability instrumentation.
 *
 * Usage:
 *   @Traceable
 *   public Order processOrder(String id) { ... }
 *
 *   @Traceable(name = "charge-card", logEntry = true)
 *   public void chargeCard(PaymentRequest req) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Traceable {

    /** Human-readable name. Defaults to ClassName.methodName. */
    String name() default "";

    /** Emit a LOG event when the method is entered. */
    boolean logEntry() default false;

    /** Emit a METRIC event with execution time on normal completion. */
    boolean trackDuration() default true;
}