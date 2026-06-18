package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.InstallUrlResponse;
import br.com.llfashion.whatsappcheckout.service.NuvemshopOAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nuvemshop/oauth")
public class NuvemshopOAuthController {

    private final NuvemshopOAuthService oauthService;

    public NuvemshopOAuthController(NuvemshopOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @GetMapping("/install-url")
    public InstallUrlResponse getInstallUrl() {
        return oauthService.getInstallUrl();
    }

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code) {
        return oauthService.processCallback(code);
    }
}
