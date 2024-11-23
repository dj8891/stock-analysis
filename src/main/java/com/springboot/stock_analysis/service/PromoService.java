package com.springboot.stock_analysis.service;

import com.springboot.stock_analysis.model.PromoCode;
import com.springboot.stock_analysis.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PromoService {

    private final PromoCodeRepository promoCodeRepository;
    public PromoService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    public PromoCode validatePromoCode(String code) {
        return promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code"));
    }
}
