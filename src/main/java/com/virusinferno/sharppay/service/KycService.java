package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.Transaction;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.TransactionRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String processKyc(String email, MultipartFile idCard, MultipartFile selfie) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));

        if ("VERIFIED".equals(user.getKycStatus())) {
            throw new RuntimeException("User is already KYC Verified!");
        }

        try {
            String idCardKey = "kyc-docs/" + user.getId() + "/id_card_" + UUID.randomUUID() + ".jpg";
            String selfieKey = "kyc-docs/" + user.getId() + "/selfie_" + UUID.randomUUID() + ".jpg";

            uploadToS3(idCardKey, idCard);
            uploadToS3(selfieKey, selfie);

            boolean isMatch = compareFacesInS3(idCardKey, selfieKey);

            if (isMatch) {
                user.setKycStatus("VERIFIED");
                user.setSelfieS3Key(selfieKey);
                userRepository.save(user);

                // ==========================================
                // THE ₦50,000 WELCOME BONUS INJECTION
                // ==========================================
                Account userAccount = accountRepository.findByUser(user)
                        .orElseThrow(() -> new RuntimeException("Account not found"));

                BigDecimal bonusAmount = new BigDecimal("50000.00");
                userAccount.setBalance(userAccount.getBalance().add(bonusAmount));
                accountRepository.save(userAccount);

                Transaction bonusTx = new Transaction();
                bonusTx.setTransactionId("BONUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                bonusTx.setSessionId("SES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                bonusTx.setSenderAccount(null); // System Generated
                bonusTx.setReceiverAccount(userAccount);
                bonusTx.setAmount(bonusAmount);
                bonusTx.setTransactionType("WELCOME_BONUS");
                bonusTx.setStatus("COMPLETED");
                bonusTx.setDescription("SharpPay ₦50,000 KYC Welcome Bonus!");
                transactionRepository.save(bonusTx);
                // ==========================================

                return "KYC Verification Successful! ₦50,000 Welcome Bonus has been credited to your wallet.";
            } else {
                user.setKycStatus("FAILED");
                userRepository.save(user);
                throw new RuntimeException("KYC Failed: Faces do not match!");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process image files: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("AWS Error: " + e.getMessage());
        }
    }

    public String verifyLiveness(String email, MultipartFile freshSelfie) {
        User user = userRepository.findByEmail(email).orElseThrow();

        if (user.getSelfieS3Key() == null) {
            throw new RuntimeException("You must complete full KYC onboarding before using liveness features.");
        }

        try {
            String tempKey = "liveness-checks/" + user.getId() + "/fresh_selfie_" + UUID.randomUUID() + ".jpg";
            uploadToS3(tempKey, freshSelfie);

            boolean isMatch = compareFacesInS3(user.getSelfieS3Key(), tempKey);

            if (isMatch) {
                user.setLivenessVerifiedAt(LocalDateTime.now());
                userRepository.save(user);
                return "Liveness verified! You have 5 minutes to complete your high-value transfer.";
            } else {
                throw new RuntimeException("Biometric failed: Face does not match the account owner.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Liveness check failed: " + e.getMessage());
        }
    }

    private void uploadToS3(String s3Key, MultipartFile file) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName).key(s3Key).contentType(file.getContentType()).build();
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    private boolean compareFacesInS3(String sourceKey, String targetKey) {
        Image sourceImage = Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(sourceKey).build()).build();
        Image targetImage = Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(targetKey).build()).build();
        CompareFacesRequest request = CompareFacesRequest.builder().sourceImage(sourceImage).targetImage(targetImage).similarityThreshold(85F).build();
        return !rekognitionClient.compareFaces(request).faceMatches().isEmpty();
    }
}