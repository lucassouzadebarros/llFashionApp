package br.com.llfashion.whatsappcheckout;

import br.com.llfashion.whatsappcheckout.config.CheckoutProperties;
import br.com.llfashion.whatsappcheckout.config.NuvemshopProperties;
import br.com.llfashion.whatsappcheckout.config.StockSyncProperties;
import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({NuvemshopProperties.class, WhatsAppProperties.class, CheckoutProperties.class, StockSyncProperties.class})
public class WhatsappCheckoutNuvemshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsappCheckoutNuvemshopApplication.class, args);
    }
}
