package com.be.trainingproduct.controller;

import com.be.categorymatcher.dto.ProductIndexRebuildResponse;
import com.be.global.response.CommonResponse;
import com.be.trainingproduct.dto.ProductCategoryFeedbackRequest;
import com.be.trainingproduct.dto.ProductCategoryFeedbackResponse;
import com.be.trainingproduct.service.TrainingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/training-products")
@Tag(name = "Training Products", description = "Historical product vector index and correction feedback API")
@RequiredArgsConstructor
public class TrainingProductController {
    private final TrainingProductService trainingProductService;

    @Operation(
            summary = "Rebuild historical product FAISS index",
            description = "Reads product names from column D and my category codes from column T. Multiple .xlsx files are accepted."
    )
    @PostMapping(value = "/rebuild", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProductIndexRebuildResponse> rebuild(
            @Parameter(example = "uno1969", required = true)
            @RequestParam("userKey") String userKey,
            @Parameter(description = "Historical product Excel files", required = true)
            @RequestParam("files") List<MultipartFile> files
    ) {
        ProductIndexRebuildResponse response = trainingProductService.rebuildIndex(userKey, files);
        return CommonResponse.success(response, response.message());
    }

    @Operation(
            summary = "Save user category correction",
            description = "Stores a confirmed category in MySQL and immediately updates the user's FAISS product index."
    )
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommonResponse<ProductCategoryFeedbackResponse> feedback(
            @RequestBody ProductCategoryFeedbackRequest request
    ) {
        ProductCategoryFeedbackResponse response = trainingProductService.addFeedback(request);
        return CommonResponse.success(response, response.message());
    }
}
