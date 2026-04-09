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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;
    private final UserRepository userRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public String processKyc(String email, MultipartFile idCard, MultipartFile selfie) {
        // 1. Verify the user exists securely via their JWT email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        // Check if they are already verified
        if ("VERIFIED".equals(user.getKycStatus())) {
            throw new RuntimeException("User is already KYC Verified!");
        }

        try {
            // 2. Generate unique file names for S3 so images don't overwrite each other
            String idCardKey = "kyc-docs/" + user.getId() + "/id_card_" + UUID.randomUUID() + ".jpg";
            String selfieKey = "kyc-docs/" + user.getId() + "/selfie_" + UUID.randomUUID() + ".jpg";

            // 3. Upload both files securely to your AWS S3 Bucket
            uploadToS3(idCardKey, idCard);
            uploadToS3(selfieKey, selfie);

            // 4. Ask AWS Rekognition AI to compare the two faces
            boolean isMatch = compareFacesInS3(idCardKey, selfieKey);

            // 5. Update the Database based on the AI's decision
            if (isMatch) {
                user.setKycStatus("VERIFIED");
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

    private void uploadToS3(String s3Key, MultipartFile file) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    private boolean compareFacesInS3(String sourceKey, String targetKey) {
        // Point Rekognition to the ID Card in your bucket
        Image sourceImage = Image.builder()
                .s3Object(S3Object.builder().bucket(bucketName).name(sourceKey).build())
                .build();

        // Point Rekognition to the Selfie in your bucket
        Image targetImage = Image.builder()
                .s3Object(S3Object.builder().bucket(bucketName).name(targetKey).build())
                .build();

        // Build the AI request (We are asking for an 85% minimum similarity match)
        CompareFacesRequest request = CompareFacesRequest.builder()
                .sourceImage(sourceImage)
                .targetImage(targetImage)
                .similarityThreshold(85F)
                .build();

        CompareFacesResponse response = rekognitionClient.compareFaces(request);

        // If the list of face matches is NOT empty, the AI found a match!
        return !response.faceMatches().isEmpty();
    }
}