package br.com.llfashion.whatsappcheckout.client;

import br.com.llfashion.whatsappcheckout.dto.response.ViaCepAddressResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
public class ViaCepClient {

    private static final Logger log = LoggerFactory.getLogger(ViaCepClient.class);
    private static final String BASE_URL = "https://viacep.com.br";

    private final WebClient webClient;

    public ViaCepClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    public Optional<ViaCepAddressResponse> buscarEndereco(String postalCode) {
        try {
            ViaCepAddressResponse response = webClient.get()
                    .uri("/ws/{postalCode}/json/", postalCode)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ViaCepAddressResponse.class)
                    .block();

            if (response == null || response.notFound()) {
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (WebClientRequestException exception) {
            log.warn("Falha de conexão ao consultar CEP no ViaCEP. cep={}, erro={}", postalCode, exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Erro ao consultar CEP no ViaCEP. cep={}, erro={}", postalCode, exception.getMessage());
            return Optional.empty();
        }
    }
}
