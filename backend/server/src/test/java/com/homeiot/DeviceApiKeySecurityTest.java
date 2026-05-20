package com.homeiot;

import com.homeiot.member.entity.Member;
import com.homeiot.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.device-api-key=test-device-key")
class DeviceApiKeySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void shouldRejectDeviceRegisterWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-secure",
                                  "name": "secure board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.0"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptDeviceRegisterWithApiKeyAndRequireDashboardLogin() throws Exception {
        mockMvc.perform(post("/api/devices/register")
                        .header("X-Device-Api-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-secure-ok",
                                  "name": "secure board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.device_api_key").isString());

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/devices").with(masterUser()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAuthenticateAdminSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        ensureMember("master@homeiot.local", "ROLE_MASTER");

        mockMvc.perform(get("/api/auth/me").with(masterUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("master@homeiot.local"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_MASTER"));
    }

    @Test
    void shouldRequireIssuedDeviceKeyForDeviceEndpoints() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/devices/register")
                        .header("X-Device-Api-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-issued-key",
                                  "name": "secure board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String issuedKey = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.device_api_key");

        mockMvc.perform(post("/api/devices/esp32s3-issued-key/health")
                        .header("X-Device-Api-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "online": true
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/devices/esp32s3-issued-key/health")
                        .header("X-Device-Api-Key", issuedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "online": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/devices/esp32s3-issued-key/metadata")
                        .with(masterUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "display_name": "Secure board"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void shouldClaimDeviceForAuthenticatedUserWithIssuedDeviceKey() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/devices/register")
                        .header("X-Device-Api-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-claimable",
                                  "name": "claimable board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String issuedKey = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.device_api_key");
        ensureMember("owner@homeiot.local", "ROLE_USER");
        ensureMember("other@homeiot.local", "ROLE_USER");

        mockMvc.perform(post("/api/devices/claim")
                        .with(appUser("owner@homeiot.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-claimable",
                                  "device_api_key": "wrong-key"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/devices/claim")
                        .with(appUser("owner@homeiot.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-claimable",
                                  "device_api_key": "%s",
                                  "display_name": "Living sensor",
                                  "room": "Living Room"
                                }
                                """.formatted(issuedKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.device_id").value("esp32s3-claimable"))
                .andExpect(jsonPath("$.data.display_name").value("Living sensor"))
                .andExpect(jsonPath("$.data.room").value("Living Room"));

        mockMvc.perform(get("/api/devices").with(appUser("owner@homeiot.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].device_id", hasItem("esp32s3-claimable")));

        mockMvc.perform(post("/api/devices/claim")
                        .with(appUser("other@homeiot.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-claimable",
                                  "device_api_key": "%s"
                                }
                                """.formatted(issuedKey)))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor masterUser() {
        return user("master@homeiot.local").roles("MASTER");
    }

    private RequestPostProcessor appUser(String email) {
        return user(email).roles("USER");
    }

    private void ensureMember(String email, String role) {
        memberRepository.findByEmail(email)
                .orElseGet(() -> memberRepository.save(new Member(
                        email,
                        email,
                        null,
                        Member.AuthProvider.LOCAL,
                        List.of(role)
                )));
    }
}