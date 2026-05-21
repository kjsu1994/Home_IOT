package com.homeiot.device.controller;

import com.homeiot.common.dto.ApiResponse;
import com.homeiot.device.dto.HomeCreateRequest;
import com.homeiot.device.dto.HomeMemberAssignRequest;
import com.homeiot.device.dto.HomeResponse;
import com.homeiot.device.service.HomeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/homes")
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public ApiResponse<List<HomeResponse>> getHomes() {
        return ApiResponse.ok(homeService.getHomes());
    }

    @PostMapping
    public ApiResponse<HomeResponse> createHome(@RequestBody HomeCreateRequest request) {
        return ApiResponse.ok(homeService.createHome(request));
    }

    @PostMapping("/{homeId}/members")
    public ApiResponse<HomeResponse> addMember(@PathVariable Long homeId,
                                               @Valid @RequestBody HomeMemberAssignRequest request) {
        return ApiResponse.ok(homeService.addMember(homeId, request));
    }

    @PostMapping("/{homeId}/devices/{deviceId}")
    public ApiResponse<HomeResponse> assignDevice(@PathVariable Long homeId,
                                                  @PathVariable String deviceId) {
        return ApiResponse.ok(homeService.assignDevice(homeId, deviceId));
    }
}
