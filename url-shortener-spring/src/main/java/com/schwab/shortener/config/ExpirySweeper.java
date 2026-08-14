package com.schwab.shortener.config;

import com.schwab.shortener.service.UrlShortenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reliability feature: periodically deactivates expired short URLs so a lookup never has to lazily discover expiry. */
@Component
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

    private final UrlShortenerService service;

    public ExpirySweeper(UrlShortenerService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void sweep() {
        int swept = service.sweepExpired();
        if (swept > 0) {
            log.info("expiry-sweeper deactivated {} expired short URL(s)", swept);
        }
    }
}
