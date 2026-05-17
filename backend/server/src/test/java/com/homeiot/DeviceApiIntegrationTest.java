package com.homeiot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterAndListDevice() throws Exception {
        mockMvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-001",
                                  "name": "living room board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.0",
                                  "ip": "192.168.123.116",
                                  "rssi": -59
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.device_id").value("esp32s3-001"));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].device_id", hasItem("esp32s3-001")))
                .andExpect(jsonPath("$.data[*].name", hasItem("living room board")));
    }

    @Test
    void shouldUpdateHealthReportStateAndCreateCommand() throws Exception {
        mockMvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-002",
                                  "name": "bedroom board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.1.1",
                                  "ip": "192.168.123.120",
                                  "rssi": -61
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/devices/esp32s3-002/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "online": true,
                                  "ip": "192.168.123.121",
                                  "rssi": -55
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/devices/esp32s3-002/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command_type": "LED_ON",
                                  "payload_json": "{}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.command_type").value("LED_ON"))
                .andExpect(jsonPath("$.data.status").value("SENT"));

        mockMvc.perform(post("/api/devices/esp32s3-002/state/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "led_on": true,
                                  "wifi_connected": true,
                                  "ota_ready": true,
                                  "raw_state_json": "{\\"led_on\\":true}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.led_on").value(true))
                .andExpect(jsonPath("$.data.ota_ready").value(true));

        mockMvc.perform(get("/api/devices/esp32s3-002/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.led_on").value(true))
                .andExpect(jsonPath("$.data.wifi_connected").value(true));

        mockMvc.perform(get("/api/devices/esp32s3-002/commands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    void shouldCreateTelemetryAndOtaEvent() throws Exception {
        mockMvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "device_id": "esp32s3-003",
                                  "name": "kitchen board",
                                  "device_type": "ESP32_S3",
                                  "fw_version": "0.2.0",
                                  "ip": "192.168.123.130",
                                  "rssi": -63
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/devices/esp32s3-003/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "wifi_signal",
                                  "payload_json": "{\\"rssi\\":-63}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_type").value("wifi_signal"));

        mockMvc.perform(get("/api/devices/esp32s3-003/telemetry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].event_type").value("wifi_signal"));

        mockMvc.perform(post("/api/devices/esp32s3-003/ota/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from_version": "0.1.9",
                                  "to_version": "0.2.0",
                                  "status": "SUCCEEDED",
                                  "message": "ota done"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ota event recorded"));
    }
}
