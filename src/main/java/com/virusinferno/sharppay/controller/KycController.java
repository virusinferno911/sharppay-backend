package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping("/verify")
    public ResponseEntity<String> verifyIdentity(
            @RequestParam("idFront") MultipartFile idFront,
            @RequestParam("idBack") MultipartFile idBack,
            @RequestParam("liveSelfie") MultipartFile liveSelfie,
            Principal principal) {

        String result = kycService.processKyc(principal.getName(), idFront, idBack, liveSelfie);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/liveness")
    public ResponseEntity<String> verifyLiveness(
            @RequestParam("liveSelfie") MultipartFile liveSelfie,
            Principal principal) {

        String result = kycService.verifyLiveness(principal.getName(), liveSelfie);
        return ResponseEntity.ok(result);
    }
}