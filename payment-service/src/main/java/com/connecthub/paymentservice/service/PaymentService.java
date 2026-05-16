package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.config.KafkaConfig;
import com.connecthub.paymentservice.dto.AuthUserSummaryResponse;
import com.connecthub.paymentservice.dto.CreateOrderRequest;
import com.connecthub.paymentservice.dto.CreateOrderResponse;
import com.connecthub.paymentservice.dto.CreditTopupEvent;
import com.connecthub.paymentservice.dto.PaymentConfigResponse;
import com.connecthub.paymentservice.dto.PaymentHistoryResponse;
import com.connecthub.paymentservice.dto.PaymentVerificationRequest;
import com.connecthub.paymentservice.dto.PaymentVerificationResponse;
import com.connecthub.paymentservice.dto.TopUpPlanResponse;
import com.connecthub.paymentservice.entity.Payment;
import com.connecthub.paymentservice.repository.PaymentRepository;
import com.connecthub.paymentservice.util.SignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
/**
 * Implements the end-to-end payment flow: plan resolution, provider order
 * creation, payment verification, credit event publication, and receipt email.
 */
@Slf4j
public class PaymentService {

    private static final String CURRENCY = "INR";
    private static final String INTERNAL_TOPUP_HEADER = "X-ConnectHub-Topup-Secret";
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CREDITED = "CREDITED";
    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com";

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final PaymentPlanCatalog paymentPlanCatalog;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentReceiptEmailService paymentReceiptEmailService;

    private final ConcurrentMap<String, Object> paymentLocks = new ConcurrentHashMap<>();

    @Value("${razorpay.key-id:place-holder-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:place-holder-secret}")
    private String razorpayKeySecret;

    @Value("${app.auth-service-base-url:http://localhost:9002}")
    private String authServiceBaseUrl;

    @Value("${app.auth-service-topup-secret:connecthub-topup-secret-change-me}")
    private String authServiceTopupSecret;

    public PaymentService(
            PaymentRepository paymentRepository,
            RestTemplate restTemplate,
            PaymentPlanCatalog paymentPlanCatalog,
            KafkaTemplate<String, Object> kafkaTemplate,
            PaymentReceiptEmailService paymentReceiptEmailService) {
        this.paymentRepository = paymentRepository;
        this.restTemplate = restTemplate;
        this.paymentPlanCatalog = paymentPlanCatalog;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentReceiptEmailService = paymentReceiptEmailService;
    }

    public PaymentConfigResponse getConfig() {
        return paymentPlanCatalog.buildConfig("razorpay", razorpayKeyId);
    }

    public AuthUserSummaryResponse getUserSummary(String userId) {
        return fetchRequiredAuthUser(requireUserId(userId));
    }

    public List<PaymentHistoryResponse> getHistory(String userId) {
        String uid = requireUserId(userId);
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(uid)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    /**
     * Resolves the requested plan, creates the provider-side order, and stores
     * the local audit row that the verification step will later update.
     */
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        validateRazorpayConfiguration();
        String userId = requireUserId(request.getUserId());
        TopUpPlanResponse plan = resolvePlan(request);
        String receipt = "topup-" + UUID.randomUUID().toString().substring(0, 8);
        ReceiptContact contact = resolveReceiptContact(request, userId);
        long amountInPaise = toPaise(plan.amount());

        try {
            Map<?, ?> providerOrder = razorpayRequest(
                    "/v1/orders",
                    HttpMethod.POST,
                    buildCreateOrderPayload(plan, userId, receipt));
            String orderId = readString(providerOrder, "id");

            if (!StringUtils.hasText(orderId)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Razorpay did not return a valid order id");
            }

            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .amount(plan.amount().doubleValue())
                    .amountInPaise(amountInPaise)
                    .currency(CURRENCY)
                    .planCode(plan.code())
                    .planName(plan.title())
                    .status(STATUS_CREATED)
                    .userId(userId)
                    .customerName(contact.customerName())
                    .customerEmail(contact.customerEmail())
                    .credits(plan.credits())
                    .receipt(receipt)
                    .build();
            paymentRepository.save(payment);

            return new CreateOrderResponse(
                    orderId,
                    "razorpay",
                    razorpayKeyId,
                    null,
                    CURRENCY,
                    plan.amount().setScale(2, RoundingMode.HALF_UP),
                    amountInPaise,
                    plan.credits(),
                    plan.code(),
                    plan.title(),
                    receipt,
                    userId,
                    "Razorpay order created successfully.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Unexpected error creating Razorpay order", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create Razorpay order: " + ex.getMessage());
        }
    }

    /**
     * Validates the provider callback/signature and then delegates to the
     * guarded payment completion flow.
     */
    public PaymentVerificationResponse verifyPayment(PaymentVerificationRequest request) {
        validateRazorpayConfiguration();
        String orderId = normalizeRequired(request.getOrderId(), "Order ID is required");
        String paymentId = normalizeRequired(request.getPaymentId(), "Payment ID is required");
        String signature = normalizeRequired(request.getSignature(), "Payment signature is required");
        return completePayment(orderId, paymentId, signature);
    }

    /**
     * Finalizes a payment exactly once per order by verifying provider state,
     * updating the payment record, and publishing the credit sync event.
     */
    private PaymentVerificationResponse completePayment(String orderId, String paymentId, String signature) {
        Object lock = paymentLock(orderId);
        try {
            synchronized (lock) {
                Payment payment = paymentRepository.findByOrderId(orderId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Payment order not found: " + orderId));

                ensureMatchingPaymentId(payment, paymentId);
                verifySignature(orderId, paymentId, signature);

                if (STATUS_CREDITED.equalsIgnoreCase(payment.getStatus())) {
                    return buildVerificationResponse(payment, true, true,
                            "Credits already synced for this payment", null);
                }

                if (!STATUS_PAID.equalsIgnoreCase(payment.getStatus())) {
                    confirmCapturedPayment(orderId, paymentId, payment);
                    payment.setPaymentId(paymentId);
                    payment.setSignature(signature);
                    payment.setStatus(STATUS_PAID);
                    payment.setVerifiedAt(LocalDateTime.now());
                    payment.setLastError(null);
                    paymentRepository.save(payment);
                } else {
                    payment.setPaymentId(paymentId);
                    payment.setSignature(signature);
                    payment.setLastError(null);
                    paymentRepository.save(payment);
                }

                CreditTopupEvent event = new CreditTopupEvent(
                        payment.getUserId(),
                        payment.getCredits(),
                        payment.getOrderId(),
                        payment.getPaymentId());

                try {
                    applyCreditsViaAuthService(payment);
                    payment.setStatus(STATUS_CREDITED);
                    payment.setCreditedAt(LocalDateTime.now());
                    payment.setLastError(null);
                    payment = ensureReceiptContact(payment);
                    paymentRepository.save(payment);
                    Payment receiptPayment = payment;
                    java.util.concurrent.CompletableFuture.runAsync(() -> sendReceiptEmail(receiptPayment));
                    return buildVerificationResponse(payment, true, true,
                            "Payment verified and credits added!", null);
                } catch (Exception ex) {
                    log.error("Direct credit sync failed for order {}", orderId, ex);
                    String message = publishPendingCreditSync(payment, event, ex);
                    payment.setLastError(message);
                    paymentRepository.save(payment);
                    return buildVerificationResponse(payment, true, false, message, message);
                }
            }
        } finally {
            paymentLocks.remove(orderId, lock);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> razorpayRequest(String path, HttpMethod method, Object body) {
        HttpEntity<?> entity = method == HttpMethod.GET
                ? new HttpEntity<>(razorpayHeaders())
                : new HttpEntity<>(body, razorpayHeaders());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    RAZORPAY_API_BASE + path,
                    method,
                    entity,
                    Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from Razorpay");
            }
            return responseBody;
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (HttpClientErrorException ex) {
            log.error("Razorpay client error on {} {}: {} {}", method, path,
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            HttpStatus mappedStatus = ex.getStatusCode().is4xxClientError()
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(mappedStatus,
                    "Razorpay request failed: " + extractErrorMessage(ex.getResponseBodyAsString()));
        } catch (HttpServerErrorException ex) {
            log.error("Razorpay server error on {} {}: {} {}", method, path,
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay is unavailable right now. " + extractErrorMessage(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            log.error("Unexpected Razorpay API error on {} {}", method, path, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to reach Razorpay: " + ex.getMessage());
        }
    }

    private void confirmCapturedPayment(String orderId, String paymentId, Payment payment) {
        RazorpayPaymentDetails paymentDetails = fetchPaymentDetails(paymentId);
        validateProviderPayment(paymentDetails, orderId, payment);

        if (paymentDetails.captured()) {
            return;
        }

        if ("authorized".equalsIgnoreCase(paymentDetails.status())) {
            RazorpayPaymentDetails capturedPayment = capturePayment(
                    paymentId,
                    payment.getAmountInPaise(),
                    payment.getCurrency());
            validateProviderPayment(capturedPayment, orderId, payment);
            if (capturedPayment.captured()) {
                return;
            }
            paymentDetails = capturedPayment;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Razorpay payment is not captured. Current status: " + paymentDetails.status());
    }

    private RazorpayPaymentDetails fetchPaymentDetails(String paymentId) {
        Map<?, ?> body = razorpayRequest("/v1/payments/" + paymentId, HttpMethod.GET, null);
        return toPaymentDetails(body);
    }

    private RazorpayPaymentDetails capturePayment(String paymentId, Long amountInPaise, String currency) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInPaise);
        payload.put("currency", currency);
        Map<?, ?> body = razorpayRequest("/v1/payments/" + paymentId + "/capture", HttpMethod.POST, payload);
        return toPaymentDetails(body);
    }

    private RazorpayPaymentDetails toPaymentDetails(Map<?, ?> body) {
        String status = readString(body, "status");
        boolean captured = Boolean.TRUE.equals(body.get("captured")) || "captured".equalsIgnoreCase(status);
        return new RazorpayPaymentDetails(
                readString(body, "id"),
                readString(body, "order_id"),
                status,
                captured,
                readLong(body.get("amount")),
                readString(body, "currency"));
    }

    private void validateProviderPayment(RazorpayPaymentDetails paymentDetails, String orderId, Payment payment) {
        if (!StringUtils.hasText(paymentDetails.paymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay did not return a payment id");
        }
        if (StringUtils.hasText(paymentDetails.orderId()) && !orderId.equals(paymentDetails.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Razorpay payment does not belong to the requested order");
        }
        if (payment.getAmountInPaise() != null
                && paymentDetails.amountInPaise() != null
                && !payment.getAmountInPaise().equals(paymentDetails.amountInPaise())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Razorpay payment amount does not match the order amount");
        }
        if (StringUtils.hasText(payment.getCurrency())
                && StringUtils.hasText(paymentDetails.currency())
                && !payment.getCurrency().equalsIgnoreCase(paymentDetails.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Razorpay payment currency does not match the order currency");
        }
    }

    private Map<String, Object> buildCreateOrderPayload(TopUpPlanResponse plan, String userId, String receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", toPaise(plan.amount()));
        payload.put("currency", CURRENCY);
        payload.put("receipt", receipt);
        payload.put("notes", Map.of(
                "userId", userId,
                "planCode", plan.code(),
                "credits", String.valueOf(plan.credits())));
        return payload;
    }

    private HttpHeaders razorpayHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(razorpayKeyId, razorpayKeySecret, StandardCharsets.UTF_8);
        return headers;
    }

    private void verifySignature(String orderId, String paymentId, String signature) {
        String expectedSignature = SignatureUtil.generateSignature(orderId + "|" + paymentId, razorpayKeySecret);
        boolean matches = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Razorpay signature verification failed");
        }
    }

    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "Unknown provider error";
        }
        for (String field : List.of("description", "message", "error_description")) {
            String value = extractJsonField(body, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private TopUpPlanResponse resolvePlan(CreateOrderRequest request) {
        if (!StringUtils.hasText(request.getUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        if (StringUtils.hasText(request.getPlanCode())) {
            return paymentPlanCatalog.requirePlan(request.getPlanCode());
        }
        if (request.getCredits() != null) {
            validateCredits(request.getCredits());
            return paymentPlanCatalog.findByCredits(request.getCredits())
                    .orElseGet(() -> paymentPlanCatalog.buildCustomPlan(request.getCredits()));
        }
        if (request.getAmount() != null) {
            return paymentPlanCatalog.findByAmount(BigDecimal.valueOf(request.getAmount()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Amount does not match an available plan. Provide a plan code or credit amount."));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provide a plan code or credit amount");
    }

    private void validateCredits(int credits) {
        if (credits < paymentPlanCatalog.getMinCredits() || credits > paymentPlanCatalog.getMaxCredits()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Credits must be between " + paymentPlanCatalog.getMinCredits()
                            + " and " + paymentPlanCatalog.getMaxCredits());
        }
    }

    private ReceiptContact resolveReceiptContact(CreateOrderRequest request, String userId) {
        String name = normalizeOptional(request.getCustomerName());
        String email = normalizeOptional(request.getCustomerEmail());
        if (StringUtils.hasText(name) && StringUtils.hasText(email)) {
            return new ReceiptContact(name, email);
        }

        AuthUserSummaryResponse authUser = fetchAuthUser(userId);
        if (authUser != null) {
            if (!StringUtils.hasText(name)) {
                name = firstPresent(authUser.fullName(), authUser.username(), "ConnectHub User");
            }
            if (!StringUtils.hasText(email)) {
                email = normalizeOptional(authUser.email());
            }
        }
        return new ReceiptContact(name, email);
    }

    private Payment ensureReceiptContact(Payment payment) {
        if (StringUtils.hasText(payment.getCustomerEmail()) && StringUtils.hasText(payment.getCustomerName())) {
            return payment;
        }

        CreateOrderRequest fallbackRequest = new CreateOrderRequest();
        fallbackRequest.setUserId(payment.getUserId());
        fallbackRequest.setCustomerName(payment.getCustomerName());
        fallbackRequest.setCustomerEmail(payment.getCustomerEmail());
        ReceiptContact contact = resolveReceiptContact(fallbackRequest, payment.getUserId());

        if (!StringUtils.hasText(payment.getCustomerName())) {
            payment.setCustomerName(contact.customerName());
        }
        if (!StringUtils.hasText(payment.getCustomerEmail())) {
            payment.setCustomerEmail(contact.customerEmail());
        }
        return payment;
    }

    private AuthUserSummaryResponse fetchAuthUser(String userId) {
        try {
            return fetchRequiredAuthUser(userId);
        } catch (ResponseStatusException ex) {
            log.warn("Could not fetch auth user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private AuthUserSummaryResponse fetchRequiredAuthUser(String userId) {
        try {
            AuthUserSummaryResponse response = restTemplate.exchange(
                    authServiceBaseUrl + "/auth/users/{uid}",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    AuthUserSummaryResponse.class,
                    userId).getBody();
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
            }
            return response;
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (Exception ex) {
            log.warn("Could not fetch auth user {}: {}", userId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to load user account right now");
        }
    }

    private void applyCreditsViaAuthService(Payment payment) {
        if (!StringUtils.hasText(authServiceTopupSecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment verified, but credit sync secret is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOPUP_HEADER, authServiceTopupSecret.trim());

        try {
            restTemplate.exchange(
                    authServiceBaseUrl + "/auth/users/{uid}/translation-credits/top-up"
                            + "?credits={credits}&orderId={orderId}&paymentId={paymentId}",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class,
                    payment.getUserId(),
                    payment.getCredits(),
                    payment.getOrderId(),
                    payment.getPaymentId());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment verified, but auth-service credit sync failed: " + ex.getStatusCode());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment verified, but auth-service credit sync is unavailable.");
        }
    }

    private String publishPendingCreditSync(Payment payment, CreditTopupEvent event, Exception failure) {
        try {
            kafkaTemplate.send(KafkaConfig.CREDIT_TOPUP_TOPIC, payment.getUserId(), event)
                    .get(10, TimeUnit.SECONDS);
            return "Payment verified, but credit confirmation is pending. Retry verification if the balance does not update shortly.";
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Kafka fallback credit sync interrupted for order {}", payment.getOrderId(), interruptedException);
            String rootMessage = failure.getMessage();
            if (!StringUtils.hasText(rootMessage)) {
                rootMessage = "auth-service credit sync failed";
            }
            return "Payment verified, but credits could not be added automatically. "
                    + "Retry verification or contact support. Cause: " + rootMessage;
        } catch (Exception kafkaException) {
            log.error("Kafka fallback credit sync failed for order {}", payment.getOrderId(), kafkaException);
            String rootMessage = failure.getMessage();
            if (!StringUtils.hasText(rootMessage)) {
                rootMessage = "auth-service credit sync failed";
            }
            return "Payment verified, but credits could not be added automatically. "
                    + "Retry verification or contact support. Cause: " + rootMessage;
        }
    }

    private void sendReceiptEmail(Payment payment) {
        try {
            paymentReceiptEmailService.sendReceipt(payment);
            if (StringUtils.hasText(payment.getCustomerEmail())) {
                payment.setReceiptEmailedAt(LocalDateTime.now());
                paymentRepository.save(payment);
            }
        } catch (Exception ex) {
            log.error("Receipt email failed for order {}", payment.getOrderId(), ex);
            payment.setLastError("Receipt email failed: " + ex.getMessage());
            paymentRepository.save(payment);
        }
    }

    private PaymentHistoryResponse toHistoryResponse(Payment payment) {
        return new PaymentHistoryResponse(
                payment.getOrderId(),
                payment.getPaymentId(),
                payment.getPlanCode(),
                payment.getPlanName(),
                payment.getCredits(),
                payment.getAmount() == null
                        ? null
                        : BigDecimal.valueOf(payment.getAmount()).setScale(2, RoundingMode.HALF_UP),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getUserId(),
                payment.getCreatedAt(),
                payment.getVerifiedAt(),
                payment.getCreditedAt(),
                payment.getLastError());
    }

    private PaymentVerificationResponse buildVerificationResponse(
            Payment payment,
            boolean verified,
            boolean credited,
            String message,
            String lastError) {
        return new PaymentVerificationResponse(
                verified,
                credited,
                message,
                payment.getOrderId(),
                payment.getPaymentId(),
                payment.getStatus(),
                payment.getPlanCode(),
                payment.getPlanName(),
                credited && payment.getCredits() != null ? payment.getCredits() : 0,
                null,
                null,
                lastError);
    }

    private void validateRazorpayConfiguration() {
        if (!StringUtils.hasText(razorpayKeyId) || "place-holder-id".equals(razorpayKeyId)
                || !StringUtils.hasText(razorpayKeySecret) || "place-holder-secret".equals(razorpayKeySecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Razorpay credentials are not configured. Please set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }
    }

    private void ensureMatchingPaymentId(Payment payment, String paymentId) {
        if (StringUtils.hasText(payment.getPaymentId()) && !paymentId.equals(payment.getPaymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A different payment id is already linked to this order.");
        }
    }

    private Object paymentLock(String orderId) {
        return paymentLocks.computeIfAbsent(orderId, ignored -> new Object());
    }

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        return userId.trim();
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private String readString(Map<?, ?> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractJsonField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int fieldIndex = body.indexOf(marker);
        if (fieldIndex < 0) {
            return null;
        }
        int colonIndex = body.indexOf(':', fieldIndex + marker.length());
        int firstQuote = colonIndex >= 0 ? body.indexOf('"', colonIndex + 1) : -1;
        int secondQuote = firstQuote >= 0 ? body.indexOf('"', firstQuote + 1) : -1;
        if (firstQuote < 0 || secondQuote < 0) {
            return null;
        }
        return body.substring(firstQuote + 1, secondQuote);
    }

    private record ReceiptContact(String customerName, String customerEmail) {}

    private record RazorpayPaymentDetails(
            String paymentId,
            String orderId,
            String status,
            boolean captured,
            Long amountInPaise,
            String currency
    ) {}
}
