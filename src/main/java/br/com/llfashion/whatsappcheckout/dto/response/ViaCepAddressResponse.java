package br.com.llfashion.whatsappcheckout.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ViaCepAddressResponse(
        String cep,
        String logradouro,
        String bairro,
        String localidade,
        String uf,
        Boolean erro
) {

    public boolean notFound() {
        return Boolean.TRUE.equals(erro);
    }
}
