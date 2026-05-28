package com.voicepay.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
@Slf4j
public class SignatureService {

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            // Buscamos guardar las claves en la carpeta "data" en el directorio raíz o en el directorio actual
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                // Probamos en el directorio padre si estamos en el subproyecto
                dataDir = new File("../data");
                if (!dataDir.exists()) {
                    dataDir = new File("data");
                    dataDir.mkdirs();
                }
            }

            File privateKeyFile = new File(dataDir, "signature_private.key");
            File publicKeyFile = new File(dataDir, "signature_public.key");

            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
                byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
                log.info("Claves de firma cargadas con éxito desde: {}", dataDir.getAbsolutePath());
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();

                Files.write(privateKeyFile.toPath(), privateKey.getEncoded());
                Files.write(publicKeyFile.toPath(), publicKey.getEncoded());
                log.info("Nuevas claves de firma RSA generadas y guardadas en: {}", dataDir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error al inicializar las claves criptográficas para firmas de informes", e);
            // Fallback a claves en memoria para no romper el arranque del microservicio si hay problemas de I/O
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
                log.warn("Se han generado claves RSA en memoria debido a un error al guardar/cargar desde el disco.");
            } catch (Exception ex) {
                throw new RuntimeException("Error fatal al inicializar el proveedor de firmas", ex);
            }
        }
    }

    public String sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
        } catch (Exception e) {
            log.error("Error al generar firma digital", e);
            throw new RuntimeException("Error al firmar los datos del informe", e);
        }
    }

    public boolean verify(byte[] data, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data);
            byte[] signedBytes = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signedBytes);
        } catch (Exception e) {
            log.error("Error al verificar la firma digital", e);
            return false;
        }
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
