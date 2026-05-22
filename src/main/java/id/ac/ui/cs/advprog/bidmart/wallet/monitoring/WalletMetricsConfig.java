package id.ac.ui.cs.advprog.bidmart.wallet.monitoring;

import id.ac.ui.cs.advprog.bidmart.wallet.model.HoldStatus;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.BalanceHoldRepository;
import id.ac.ui.cs.advprog.bidmart.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletMetricsConfig {

    @Bean
    public MeterBinder walletDomainMetrics(WalletRepository walletRepository,
                                           BalanceHoldRepository balanceHoldRepository) {
        return registry -> {
            Gauge.builder("wallet.wallets.total", walletRepository, WalletRepository::count)
                    .description("Total wallet records currently stored.")
                    .register(registry);

            Gauge.builder("wallet.wallets.frozen", walletRepository, WalletRepository::countByFrozenTrue)
                    .description("Total frozen wallet records.")
                    .register(registry);

            Gauge.builder("wallet.holds.active", balanceHoldRepository,
                            repository -> repository.countByStatus(HoldStatus.ACTIVE))
                    .description("Total active wallet balance holds.")
                    .register(registry);
        };
    }
}
