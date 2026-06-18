package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopProductImageResponse(
        Long id,
        String src,
        Integer position
) {
}
