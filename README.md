# SharpPay Backend API ⚙️

The secure, high-performance REST API powering the **SharpPay** Neo-Banking platform. Built with Java 17 and Spring Boot, this backend handles complex financial transactions, virtual card provisioning, JWT-based authentication, and AI-powered KYC liveness checks.

## 🛠 Tech Stack
* **Core Framework:** Java 17 + Spring Boot 3.x
* **Security:** Spring Security + JSON Web Tokens (JWT) + BCrypt Password Encoding
* **Database & ORM:** PostgreSQL (NeonDB) + Spring Data JPA (Hibernate)
* **Connection Pooling:** HikariCP
* **Cloud Integrations:** AWS S3 (Document Storage) + AWS Rekognition (Facial Liveness/KYC)
* **Email Services:** JavaMailSender (OTP delivery)

## 🏗 Core Modules & Features

### 1. Authentication & Security (`/api/v1/auth`)
* Secure user registration and login with JWT issuance.
* 6-digit OTP email verification for new accounts.
* Multi-step Transaction PIN management (Set, Change, Forgot PIN via OTP).
* Dynamic Liveness Transfer Limits (triggers face scan for large transactions).

### 2. Transactions & Wallet (`/api/v1/transactions`)
* **Transfers:** Internal SharpPay transfers and external bank routing.
* **Bill Payments:** Dedicated logic for Airtime, Data, Betting, and Utilities.
* **Ledger:** Immutable double-entry transaction logging with UUID-based Session IDs to prevent race conditions and database constraint errors.
* **Receipts:** Detailed transaction fetching for frontend PDF generation.

### 3. Virtual Cards (`/api/v1/cards`)
* Provisioning of 16-digit Virtual PANs, CVVs, and Expiry Dates.
* Strict state management allowing users to intuitively **Freeze**, **Unfreeze**, or **Disable** cards.
* Secure creation fee deduction (₦1,000) mapped directly to the user's wallet.

### 4. KYC & AI Liveness (`/api/v1/kyc`)
* Integration with **AWS Rekognition**.
* Verifies real-time face captures against stored identity documents.
* Automatically upgrades users from Tier 1 to Tier 3 Merchant status upon successful verification.

## 🚀 Getting Started

### 1. Prerequisites
* Java 17+ installed.
* Maven installed.
* A running PostgreSQL instance.
* AWS IAM credentials with S3 and Rekognition access.

### 2. Environment Configuration
Update your `src/main/resources/application.properties` with your secure credentials:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://<your-db-url>:5432/neondb?sslmode=require
spring.datasource.username=<your-db-username>
spring.datasource.password=<your-db-password>
spring.jpa.hibernate.ddl-auto=update

# JWT Secret
jwt.secret=<your-secure-base64-encoded-secret-key>

# AWS Configuration
aws.accessKeyId=<your-aws-access-key>
aws.secretKey=<your-aws-secret-key>
aws.region=<your-aws-region>
aws.s3.bucketName=<your-s3-bucket>

# SMTP Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=<your-email>
spring.mail.password=<your-app-password>
3. Build & Run
To compile the application and resolve Maven dependencies:

Bash
mvn clean install
To run the Spring Boot application locally:

Bash
mvn spring-boot:run
The API will be available at http://localhost:8080/api/v1.

🛡 CORS & Production Deployment
The application is configured to accept requests from whitelisted domains via SecurityConfig.java. The live backend is configured to accept frontend requests from https://app.virusinferno.xyz.