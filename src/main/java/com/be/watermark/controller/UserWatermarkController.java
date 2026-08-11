package com.be.watermark.controller;

import com.be.auth.security.LoginUser;
import com.be.global.response.CommonResponse;
import com.be.watermark.dto.UserWatermarkResponse;
import com.be.watermark.service.UserWatermarkService;
import com.be.watermark.service.UserWatermarkService.WatermarkImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/watermark")
@Tag(name = "사용자 워터마크", description = "사용자별 상품 이미지 워터마크 설정 API")
@RequiredArgsConstructor
public class UserWatermarkController {
    private final UserWatermarkService userWatermarkService;

    @Operation(summary = "내 워터마크 설정 조회")
    @GetMapping
    public CommonResponse<UserWatermarkResponse> get(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return CommonResponse.success(userWatermarkService.get(loginUser.id()));
    }

    @Operation(summary = "내 워터마크 이미지 조회")
    @GetMapping("/image")
    public ResponseEntity<ByteArrayResource> image(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        WatermarkImage watermark = userWatermarkService.getRequiredImage(loginUser.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(watermark.contentType()))
                .body(new ByteArrayResource(watermark.content()));
    }

    @Operation(summary = "내 워터마크 등록 또는 수정")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<UserWatermarkResponse> save(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(defaultValue = "BOTTOM_RIGHT") String position,
            @RequestParam(defaultValue = "50") int opacity,
            @RequestParam(defaultValue = "20") int sizePercent
    ) {
        return CommonResponse.success(
                userWatermarkService.save(loginUser.id(), file, position, opacity, sizePercent),
                "워터마크를 저장했습니다."
        );
    }

    @Operation(summary = "내 워터마크 삭제")
    @DeleteMapping
    public CommonResponse<Void> delete(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        userWatermarkService.delete(loginUser.id());
        return CommonResponse.success(null, "워터마크를 삭제했습니다.");
    }
}
