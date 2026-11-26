package br.mackenzie.sentineltrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do SentinelTrade. Execute esta classe e abra http://localhost:8080 no navegador. */
@SpringBootApplication
public class SentinelTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelTradeApplication.class, args);
    }
}
