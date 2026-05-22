package id.ac.ui.cs.advprog.bidmart.wallet.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Aspect
@Component
@RequiredArgsConstructor
public class WalletMetricsAspect {

    private static final String OPERATION_TOTAL = "wallet.operation";
    private static final String OPERATION_DURATION = "wallet.operation.duration";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;

    @Around("execution(* id.ac.ui.cs.advprog.bidmart.wallet.service.WalletService.*(..))")
    public Object recordWalletOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        String operation = toMetricOperation(joinPoint.getSignature().getName());
        String outcome = SUCCESS;
        String exception = NONE;

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            outcome = FAILURE;
            exception = throwable.getClass().getSimpleName();
            throw throwable;
        } finally {
            Tags tags = Tags.of(
                    "operation", operation,
                    "outcome", outcome,
                    "exception", exception);

            Counter.builder(OPERATION_TOTAL)
                    .description("Total wallet service domain operations grouped by operation and outcome.")
                    .tags(tags)
                    .register(meterRegistry)
                    .increment();

            sample.stop(Timer.builder(OPERATION_DURATION)
                    .description("Wallet service domain operation duration.")
                    .tags(tags)
                    .register(meterRegistry));
        }
    }

    private String toMetricOperation(String methodName) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < methodName.length(); index++) {
            char current = methodName.charAt(index);
            if (Character.isUpperCase(current) && index > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
