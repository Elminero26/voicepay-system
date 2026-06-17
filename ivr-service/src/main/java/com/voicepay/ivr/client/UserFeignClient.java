package com.voicepay.ivr.client;

import com.voicepay.ivr.dto.OtpGenerateRequest;
import com.voicepay.ivr.dto.OtpGenerateResponse;
import com.voicepay.ivr.dto.OtpValidateRequest;
import com.voicepay.ivr.dto.OtpValidateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service-otp", url = "${app.user-service-base-url:http://localhost:8080}")
public interface UserFeignClient {

    @PostMapping("/auth/otp/generate")
    OtpGenerateResponse generateOtp(
            @RequestBody OtpGenerateRequest request,
            @RequestHeader("Authorization") String token
    );

    @PostMapping("/auth/otp/validate")
    OtpValidateResponse validateOtp(
            @RequestBody OtpValidateRequest request,
            @RequestHeader("Authorization") String token
    );
}
