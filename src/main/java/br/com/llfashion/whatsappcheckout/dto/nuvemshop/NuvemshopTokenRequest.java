package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NuvemshopTokenRequest(
        @JsonProperty("client_id")
        String clientId,
        @JsonProperty("client_secret")
        String clientSecret,
        @JsonProperty("grant_type")
        String grantType,
        String code
) {
}
