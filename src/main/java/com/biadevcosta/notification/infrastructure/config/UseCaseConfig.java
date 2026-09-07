package com.biadevcosta.notification.infrastructure.config;

import com.biadevcosta.notification.application.port.EmailSender;
import com.biadevcosta.notification.application.port.ProcessedReminderStore;
import com.biadevcosta.notification.application.port.UserDirectory;
import com.biadevcosta.notification.application.usecase.SendAppointmentReminderUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the plain-POJO use case as a bean (it carries no Spring annotations itself). */
@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SendAppointmentReminderUseCase sendAppointmentReminderUseCase(UserDirectory userDirectory,
                                                                 EmailSender emailSender,
                                                                 ProcessedReminderStore processedReminderStore,
                                                                 Clock clock) {
        return new SendAppointmentReminderUseCase(userDirectory, emailSender, processedReminderStore, clock);
    }
}
