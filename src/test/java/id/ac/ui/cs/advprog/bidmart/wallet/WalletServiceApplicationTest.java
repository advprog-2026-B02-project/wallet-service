package id.ac.ui.cs.advprog.bidmart.wallet;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class WalletServiceApplicationTest {

    @Test
    void main_runsSpringApplication() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            WalletServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(WalletServiceApplication.class, args));
        }
    }
}
