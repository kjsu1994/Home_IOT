package com.homeiot;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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

        mockMvc.perform(get("/api/devices").with(httpBasic("admin", "homeiot-admin-change-me")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAuthenticateAdminSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "homeiot-admin-change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
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
                        .with(httpBasic("admin", "homeiot-admin-change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "display_name": "Secure board"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
