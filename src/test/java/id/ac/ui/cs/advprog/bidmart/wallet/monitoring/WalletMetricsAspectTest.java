package id.ac.ui.cs.advprog.bidmart.wallet.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletMetricsAspectTest {

    @Test
    void recordWalletOperation_normalizesOperationNameStartingWithUppercaseLetter() throws Throwable {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WalletMetricsAspect aspect = new WalletMetricsAspect(meterRegistry);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("TopUp");
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.recordWalletOperation(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(meterRegistry.find("wallet.operation")
                .tag("operation", "top_up")
                .tag("outcome", "success")
                .tag("exception", "none")
                .counter()).isNotNull();
    }
}
