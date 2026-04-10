package com.virusinferno.sharppay.service;

import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;
    private final UserRepository userRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    // 1. THE ORIGINAL ONBOARDING
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
                user.setSelfieS3Key(selfieKey); // NEW: Save the baseline selfie key!
                userRepository.save(user);
                return "KYC Verification Successful! Identity confirmed.";
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

    // 2. THE NEW LIVENESS CHECK FOR HIGH-VALUE TRANSFERS
    public String verifyLiveness(String email, MultipartFile freshSelfie) {
        User user = userRepository.findByEmail(email).orElseThrow();

        if (user.getSelfieS3Key() == null) {
            throw new RuntimeException("You must complete full KYC onboarding before using liveness features.");
        }

        try {
            // Upload the fresh selfie temporarily
            String tempKey = "liveness-checks/" + user.getId() + "/fresh_selfie_" + UUID.randomUUID() + ".jpg";
            uploadToS3(tempKey, freshSelfie);

            // Ask AWS to compare the fresh selfie to their saved KYC selfie
            boolean isMatch = compareFacesInS3(user.getSelfieS3Key(), tempKey);

            if (isMatch) {
                // Open the 5-minute trust window!
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