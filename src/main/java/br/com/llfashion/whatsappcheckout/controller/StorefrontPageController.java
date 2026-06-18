package br.com.llfashion.whatsappcheckout.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StorefrontPageController {

    @GetMapping({"/storefront", "/storefront/", "/storefront/pedido/status"})
    public String storefront() {
        return "forward:/storefront/index.html";
    }
}
