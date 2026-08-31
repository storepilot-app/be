package com.be.productimage.service;

import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.productimage.client.RemoteImageClient;
import com.be.productimage.dto.ProductImageDownloadFailure;
import com.be.productimage.dto.ProductImageDownloadPrepareResponse;
import com.be.productimage.excel.ProductImageDownloadExcelReader;
import com.be.productimage.excel.ProductImageFailureExcelWriter;
import com.be.productimage.image.JpegImageCompressor;
import com.be.productimage.image.ProductImageResizer;
import com.be.productimage.image.WatermarkApplier;
import com.be.watermark.service.UserWatermarkService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductImageDownloadService {
    private final ProductImageDownloadExcelReader excelReader;
    private final RemoteImageClient remoteImageClient;
    private final ProductImageResizer imageResizer;
    private final WatermarkApplier watermarkApplier;
    private final JpegImageCompressor imageCompressor;
    private final ProductImageFailureExcelWriter failureExcelWriter;
    private final UserWatermarkService userWatermarkService;

    public ProductImageDownloadPrepareResponse prepareImageDownloads(MultipartFile file) {
        return excelReader.read(file);
    }

    public byte[] downloadImage(
            String imageUrl,
            Integer targetSizePercent,
            Long userId,
            boolean applyWatermark
    ) {
        try {
            byte[] originalImage = remoteImageClient.download(imageUrl);
            BufferedImage resizedImage = imageResizer.resizeToSquare(originalImage);
            if (applyWatermark) {
                watermarkApplier.apply(resizedImage, userWatermarkService.getRequiredImage(userId));
            }
            return imageCompressor.compress(resizedImage, originalImage.length, targetSizePercent);
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_EXCEL_FILE, "이미지 다운로드가 중단되었습니다.");
        }
    }

    public byte[] createImageFailureExcel(List<ProductImageDownloadFailure> failures) {
        return failureExcelWriter.write(failures);
    }
}
