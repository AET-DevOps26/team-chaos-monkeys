package com.foundflow.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.JWKSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

@Configuration
public class JwtSigningConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${foundflow.auth.rsa-private-key:}") String privateKeyPem
    ) {
        KeyPair keyPair = privateKeyPem.isBlank()
                ? generateRsaKey()
                : loadKeyPairFromPem(privateKeyPem);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("foundflow-auth-key-1")
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);

        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${foundflow.auth.issuer-uri:http://localhost:8081}") String issuerUri
    ) {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate RSA key pair", exception);
        }
    }

    private static KeyPair loadKeyPairFromPem(String privateKeyPem) {
        try {
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) RsaKeyConverters.pkcs8()
                    .convert(new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8)));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));

            return new KeyPair(publicKey, privateKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load RSA key", exception);
        }
    }
}
