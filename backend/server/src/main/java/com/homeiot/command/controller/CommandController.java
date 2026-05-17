package com.homeiot.command.controller;

import com.homeiot.command.dto.DeviceCommandCreateRequest;
import com.homeiot.command.dto.DeviceCommandResponse;
import com.homeiot.command.service.CommandService;
import com.homeiot.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ApiResponse<DeviceCommandResponse> createCommand(@PathVariable String deviceId,
                                                            @Valid @RequestBody DeviceCommandCreateRequest request) {
        return ApiResponse.ok(commandService.createCommand(deviceId, request));
    }

    @GetMapping
    public ApiResponse<List<DeviceCommandResponse>> getCommands(@PathVariable String deviceId) {
        return ApiResponse.ok(commandService.getCommands(deviceId));
    }
}
