package com.springboot.stock_analysis.config;

import com.springboot.stock_analysis.model.PromoCode;
import com.springboot.stock_analysis.service.PromoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promo")
public class PromoCodeController {
    private final PromoService promoService;
    public PromoCodeController(PromoService promoService) {
        this.promoService = promoService;
    }

    @GetMapping("/validate")
    public ResponseEntity<PromoCode> validatePromoCode(@RequestParam String code) {
        try {
            PromoCode promoCode = this.promoService.validatePromoCode(code);
            return ResponseEntity.ok(promoCode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
}
