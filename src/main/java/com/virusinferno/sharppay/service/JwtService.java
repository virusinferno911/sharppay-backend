package com.virusinferno.sharppay.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // This is the master signature key for your banking app.
    // It is "VirusinfernoDigitalStudioSharpPaySecretKey2026" encoded in Base64 cryptographic format.
    private static final String SECRET_KEY = "VmlydXNpbmZlcm5vRGlnaXRhbFN0dWRpb1NoYXJwUGF5U2VjcmV0S2V5MjAyNg==";

    // 1. Generate the digital key card (Runs when a user logs in)
    public String generateToken(String userEmail) {
        return generateToken(new HashMap<>(), userEmail);
    }

    public String generateToken(Map<String, Object> extraClaims, String userEmail) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userEmail)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // Token valid for exactly 24 hours
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Validate the Token (Runs on every API request to make sure the hacker isn't forging a token)
    public boolean isTokenValid(String token, String userEmail) {
        final String extractedEmail = extractUsername(token);
        return (extractedEmail.equals(userEmail)) && !isTokenExpired(token);
    }

    // 3. Extract the User's Email from the Token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // This converts your Base64 string into a true cryptographic key
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
