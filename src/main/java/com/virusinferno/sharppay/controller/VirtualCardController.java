package com.virusinferno.sharppay.controller;

import com.virusinferno.sharppay.dto.CardCreateRequest;
import com.virusinferno.sharppay.dto.CardResponse;
import com.virusinferno.sharppay.service.VirtualCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    @PostMapping("/create")
    public ResponseEntity<CardResponse> createCard(@RequestBody CardCreateRequest request, Principal principal) {
        CardResponse response = virtualCardService.createCard(principal.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-card")
    public ResponseEntity<CardResponse> getMyCard(Principal principal) {
        CardResponse response = virtualCardService.getMyCard(principal.getName());
        return ResponseEntity.ok(response);
    }
}