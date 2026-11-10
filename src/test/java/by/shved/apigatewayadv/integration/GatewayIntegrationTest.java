package by.shved.apigatewayadv.integration;

import by.shved.apigatewayadv.dto.registration.RegistrationRequest;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayIntegrationTest {
    private static final MockWebServer userServiceMock = new MockWebServer();
    private static final MockWebServer authenticationServiceMock = new MockWebServer();
    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startMockWebServers() throws IOException {
        userServiceMock.start(8081);
        authenticationServiceMock.start(8082);
    }

    @AfterAll
    static void shutdownMockWebServers() throws IOException {
        userServiceMock.shutdown();
        authenticationServiceMock.shutdown();
    }

    @Test
    void register_shouldCreateUserAndCredentials_whenRequestIsValid() throws Exception {
        userServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(201)
                        .setHeader("Content-Type", "application/json")
                        .setBody(createdUserJson())
        );
        authenticationServiceMock.enqueue(
                new MockResponse().setResponseCode(204)
        );

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRegistrationRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.name").isEqualTo("Ivan")
                .jsonPath("$.surname").isEqualTo("Ivanov")
                .jsonPath("$.email").isEqualTo("ivan@example.com")
                .jsonPath("$.active").isEqualTo(true);

        RecordedRequest userRequest = takeRequest(userServiceMock);
        JsonNode userRequestBody = objectMapper.readTree(userRequest.getBody().readUtf8());
        RecordedRequest credentialsRequest = takeRequest(authenticationServiceMock);
        JsonNode credentialsRequestBody = objectMapper.readTree(credentialsRequest.getBody().readUtf8());

        assertEquals("POST", userRequest.getMethod());
        assertEquals("/api/v1/users", userRequest.getPath());

        assertEquals("Ivan", userRequestBody.get("name").asString());
        assertEquals("Ivanov", userRequestBody.get("surname").asString());
        assertEquals("ivan@example.com", userRequestBody.get("email").asString());

        assertEquals("POST", credentialsRequest.getMethod());
        assertEquals("/api/v1/auth/credentials", credentialsRequest.getPath());

        assertEquals(1L, credentialsRequestBody.get("userId").asLong());
        assertEquals("ivan123", credentialsRequestBody.get("login").asString());
        assertEquals("password123", credentialsRequestBody.get("password").asString());
        assertEquals("internal-secret", userRequest.getHeader("X-Internal-Secret"));
    }

    @Test
    void register_shouldNotSaveCredentials_whenUserCreationFails() throws Exception {
        userServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("User Service error")
        );

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRegistrationRequest())
                .exchange()
                .expectStatus().is5xxServerError();

        RecordedRequest userRequest = takeRequest(userServiceMock);

        assertEquals("POST", userRequest.getMethod());
        assertEquals("/api/v1/users", userRequest.getPath());
        assertNull(authenticationServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void register_shouldRollbackUser_whenCredentialsSavingFails() throws Exception {
        userServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(201)
                        .setHeader("Content-Type", "application/json")
                        .setBody(createdUserJson())
        );
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setBody("Credentials saving failed")
        );
        userServiceMock.enqueue(
                new MockResponse().setResponseCode(204)
        );

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRegistrationRequest())
                .exchange()
                .expectStatus().is5xxServerError();

        RecordedRequest createRequest = takeRequest(userServiceMock);
        RecordedRequest credentialsRequest = takeRequest(authenticationServiceMock);
        RecordedRequest deleteRequest = takeRequest(userServiceMock);

        assertEquals("POST", createRequest.getMethod());
        assertEquals("/api/v1/users", createRequest.getPath());

        assertEquals("POST", credentialsRequest.getMethod());
        assertEquals("/api/v1/auth/credentials", credentialsRequest.getPath());

        assertEquals("DELETE", deleteRequest.getMethod());
        assertEquals("/api/v1/users/1", deleteRequest.getPath());
        assertEquals("internal-secret", deleteRequest.getHeader("X-Internal-Secret"));
    }

    @Test
    void register_shouldReturnBadRequest_whenRequestIsInvalid() throws InterruptedException {
        RegistrationRequest invalidRequest = new RegistrationRequest(
                "",
                "Ivanov",
                java.time.LocalDate.of(2000, 1, 1),
                "invalid-email",
                "ivan123",
                "password123"
        );

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();

        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
        assertNull(authenticationServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void filter_shouldReturnUnauthorized_whenTokenIsMissing() throws InterruptedException {
        webTestClient.get()
                .uri("/api/v1/users")
                .exchange()
                .expectStatus().isUnauthorized();

        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
        assertNull(authenticationServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void filter_shouldReturnUnauthorized_whenTokenIsInvalid() throws InterruptedException {
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                            {
                                "valid": false,
                                "userId": null,
                                "role": null
                            }
                            """)
        );

        webTestClient.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();

        RecordedRequest validationRequest = authenticationServiceMock.takeRequest(2, TimeUnit.SECONDS);

        assertNotNull(validationRequest);
        assertEquals("POST", validationRequest.getMethod());
        assertEquals("/api/v1/auth/validate", validationRequest.getPath());
        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldForwardRequestToUserServiceWhenJwtIsValid() throws InterruptedException {
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("""
                                {
                                    "valid": true,
                                    "userId": 1,
                                    "role": "USER"
                                }
                                """)
        );
        userServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("""
                                [
                                    {
                                        "id": 1,
                                        "name": "Ivan",
                                        "surname": "Ivanov",
                                        "email": "ivan@example.com",
                                        "active": true
                                    }
                                ]
                                """)
        );

        webTestClient.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest authRequest = takeRequest(authenticationServiceMock);
        RecordedRequest userRequest = takeRequest(userServiceMock);

        assertEquals("POST", authRequest.getMethod());
        assertEquals("/api/v1/auth/validate", authRequest.getPath());
        assertEquals("Bearer valid-token", authRequest.getHeader(HttpHeaders.AUTHORIZATION));

        assertEquals("GET", userRequest.getMethod());
        assertEquals("/api/v1/users", userRequest.getPath());
        assertEquals("Bearer valid-token", userRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }

    void filter_shouldReturnServiceUnavailable_whenAuthenticationServiceIsUnavailable() throws InterruptedException {
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        );

        webTestClient.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().isEqualTo(503);

        RecordedRequest authRequest = takeRequest(authenticationServiceMock);

        assertEquals("POST", authRequest.getMethod());
        assertEquals("/api/v1/auth/validate", authRequest.getPath());
        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void filter_shouldAllowLoginWithoutJwt() throws InterruptedException {
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("""
                            {
                                "accessToken": "access-token",
                                "refreshToken": "refresh-token"
                            }
                            """)
        );

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "login": "ivan123",
                        "password": "password123"
                    }
                    """)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest authRequest = takeRequest(authenticationServiceMock);

        assertEquals("POST", authRequest.getMethod());
        assertEquals("/api/v1/auth/login", authRequest.getPath());
        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void filter_shouldAllowRefreshWithoutJwt() throws InterruptedException {
        authenticationServiceMock.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("""
                            {
                                "accessToken": "new-access-token",
                                "refreshToken": "new-refresh-token"
                            }
                            """)
        );

        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "refreshToken": "refresh-token"
                    }
                    """)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest authRequest = takeRequest(authenticationServiceMock);

        assertEquals("POST", authRequest.getMethod());
        assertEquals("/api/v1/auth/refresh", authRequest.getPath());
        assertNull(userServiceMock.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    private RegistrationRequest validRegistrationRequest() {
        return new RegistrationRequest(
                "Ivan",
                "Ivanov",
                java.time.LocalDate.of(2000, 1, 1),
                "ivan@example.com",
                "ivan123",
                "password123"
        );
    }

    private String createdUserJson() {
        return """
                {
                    "id": 1,
                    "name": "Ivan",
                    "surname": "Ivanov",
                    "birthDate": "2000-01-01",
                    "email": "ivan@example.com",
                    "active": true
                }
                """;
    }

    private RecordedRequest takeRequest(MockWebServer server) throws InterruptedException {
        RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
        assertNotNull(request, "Expected HTTP request was not received");
        return request;
    }
}
