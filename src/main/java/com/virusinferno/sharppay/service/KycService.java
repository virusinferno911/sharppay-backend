package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.model.Account;
import com.virusinferno.sharppay.model.Transaction;
import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.AccountRepository;
import com.virusinferno.sharppay.repository.TransactionRepository;
import com.virusinferno.sharppay.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
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

    private final String FACE_COLLECTION_ID = "sharppay-users-collection";

    // 1. AUTO-CREATE THE AWS FACE DATABASE ON STARTUP
    @PostConstruct
    public void initRekognitionCollection() {
        try {
            rekognitionClient.createCollection(CreateCollectionRequest.builder().collectionId(FACE_COLLECTION_ID).build());
            System.out.println("AWS Rekognition Face Collection Created!");
        } catch (ResourceAlreadyExistsException e) {
            System.out.println("AWS Rekognition Face Collection already exists. Ready for KYC.");
        }
    }

    // 2. THE NEW ENTERPRISE KYC FLOW
    public String processKyc(String email, MultipartFile idFront, MultipartFile idBack, MultipartFile liveSelfie) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found!"));

        if ("VERIFIED".equals(user.getKycStatus())) {
            throw new RuntimeException("User is already KYC Verified!");
        }

        try {
            /* * ==========================================
             * DEV NOTE: ANTI-DUPLICATION BYPASS
             * ==========================================
             * This block checks AWS Rekognition to see if the user's face is already in our system.
             * It is temporarily commented out so the main developer can repeatedly test the KYC
             * flow using their own face without triggering the "Duplicate Face" fraud block.
             * MUST BE UNCOMMENTED BEFORE DEPLOYING TO PRODUCTION.
             * ==========================================
             *
             * SdkBytes selfieBytes = SdkBytes.fromInputStream(liveSelfie.getInputStream());
             * SearchFacesByImageRequest searchReq = SearchFacesByImageRequest.builder()
             * .collectionId(FACE_COLLECTION_ID)
             * .image(Image.builder().bytes(selfieBytes).build())
             * .faceMatchThreshold(90F) // 90% confidence
             * .maxFaces(1)
             * .build();
             *
             * try {
             * SearchFacesByImageResponse searchRes = rekognitionClient.searchFacesByImage(searchReq);
             * if (!searchRes.faceMatches().isEmpty()) {
             * throw new RuntimeException("KYC Rejected: An account with this facial identity already exists in our system.");
             * }
             * } catch (InvalidParameterException e) {
             * // Ignore for first user
             * }
             */

            // B. Upload all 3 documents to S3
            String idFrontKey = "kyc-docs/" + user.getId() + "/id_front_" + UUID.randomUUID() + ".jpg";
            String idBackKey = "kyc-docs/" + user.getId() + "/id_back_" + UUID.randomUUID() + ".jpg";
            String selfieKey = "kyc-docs/" + user.getId() + "/selfie_" + UUID.randomUUID() + ".jpg";

            uploadToS3(idFrontKey, idFront);
            uploadToS3(idBackKey, idBack);
            uploadToS3(selfieKey, liveSelfie);

            // C. Compare the Live Selfie to the ID Card Front
            boolean isMatch = compareFacesInS3(idFrontKey, selfieKey);

            if (isMatch) {
                // D. Index the verified face into our AWS Database to prevent future duplicates!
                // NOTE: Because we bypassed the duplicate check, AWS might throw a warning here if you are already indexed,
                // but we will catch it so it doesn't crash your test!
                try {
                    IndexFacesRequest indexReq = IndexFacesRequest.builder()
                            .collectionId(FACE_COLLECTION_ID)
                            .image(Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(selfieKey).build()).build())
                            .externalImageId(user.getId().toString().replace("-", ""))
                            .build();
                    rekognitionClient.indexFaces(indexReq);
                } catch (Exception e) {
                    System.out.println("Dev Warning: Face already indexed in AWS, skipping indexing step for this test.");
                }

                // E. Save and Reward
                user.setKycStatus("VERIFIED");
                user.setIdS3FrontKey(idFrontKey);
                user.setIdS3BackKey(idBackKey);
                user.setSelfieS3Key(selfieKey);
                userRepository.save(user);

                injectWelcomeBonus(user);

                return "KYC Verification Successful! Identity confirmed and ₦50,000 Welcome Bonus credited.";
            } else {
                user.setKycStatus("FAILED");
                userRepository.save(user);
                throw new RuntimeException("KYC Failed: Live selfie does not match the provided ID card!");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process image files: " + e.getMessage());
        }
    }

    private void injectWelcomeBonus(User user) {
        Account userAccount = accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        BigDecimal bonusAmount = new BigDecimal("50000.00");
        userAccount.setBalance(userAccount.getBalance().add(bonusAmount));
        accountRepository.save(userAccount);

        Transaction bonusTx = new Transaction();
        bonusTx.setTransactionId("BONUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        bonusTx.setSenderAccount(null);
        bonusTx.setReceiverAccount(userAccount);
        bonusTx.setAmount(bonusAmount);
        bonusTx.setTransactionType("WELCOME_BONUS");
        bonusTx.setStatus("COMPLETED");
        bonusTx.setDescription("SharpPay ₦50,000 KYC Welcome Bonus!");
        transactionRepository.save(bonusTx);
    }

    public String verifyLiveness(String email, MultipartFile freshSelfie) {
        User user = userRepository.findByEmail(email).orElseThrow();
        if (user.getSelfieS3Key() == null) throw new RuntimeException("Complete KYC first.");

        try {
            String tempKey = "liveness-checks/" + user.getId() + "/fresh_selfie_" + UUID.randomUUID() + ".jpg";
            uploadToS3(tempKey, freshSelfie);
            if (compareFacesInS3(user.getSelfieS3Key(), tempKey)) {
                user.setLivenessVerifiedAt(LocalDateTime.now());
                userRepository.save(user);
                return "Liveness verified! You have 5 minutes.";
            } else {
                throw new RuntimeException("Biometric failed: Face does not match account owner.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Liveness check failed.");
        }
    }

    private void uploadToS3(String s3Key, MultipartFile file) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName).key(s3Key).contentType(file.getContentType()).build();
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    private boolean compareFacesInS3(String sourceKey, String targetKey) {
        Image sourceImage = Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(sourceKey).build()).build();
        Image targetImage = Image.builder().s3Object(S3Object.builder().bucket(bucketName).name(targetKey).build()).build();
        CompareFacesRequest request = CompareFacesRequest.builder().sourceImage(sourceImage).targetImage(targetImage).similarityThreshold(85F).build();
        return !rekognitionClient.compareFaces(request).faceMatches().isEmpty();
    }
}