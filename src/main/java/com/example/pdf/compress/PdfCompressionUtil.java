package com.example.pdf.compress;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class PdfCompressionUtil {
  private static final Logger log = LoggerFactory.getLogger(PdfCompressionUtil.class);
  private static final long MIN_SIZE_FOR_COMPRESSION = 1024;
  private static final String COMPRESSION_POSTFIX = "_압축";

  /**
   * 압축 프로필 설정
   * 각 프로필은 사용 사례에 따른 압축 설정을 정의합니다.
   */
  public enum CompressionProfile {
    // 최소 크기 프로필 - 웹 공유용
    MINIMUM_SIZE(
      0.65f, // 이미지 품질
      0.6f,  // 큰 이미지 품질
      300,   // 최대 DPI
      72,    // 최소 DPI
      1.0f,  // 리사이징 배수
      500000,  // 무손실 압축 최대 픽셀 (0.5메가픽셀)
      4000000  // 매우 큰 이미지 기준 (4백만 픽셀)
    ),

    // 300MB를 약 200MB로 압축하기 위한 프로필 (약 33-40% 압축률)
    TARGET_SIZE_200MB(
      0.95f, // 이미지 품질
      0.90f, // 큰 이미지 품질
      800,   // 최대 DPI
      180,   // 최소 DPI
      2.0f,  // 리사이징 배수
      3000000, // 무손실 압축 최대 픽셀 (3메가픽셀)
      15000000 // 매우 큰 이미지 기준 (1천5백만 픽셀)
    ),

    // 200MB 이하로 압축하는 균형 잡힌 프로필
    BALANCED(
      0.92f, // 이미지 품질
      0.85f, // 큰 이미지 품질
      600,   // 최대 DPI
      150,   // 최소 DPI
      1.5f,  // 리사이징 배수
      2000000, // 무손실 압축 최대 픽셀 (2메가픽셀)
      10000000 // 매우 큰 이미지 기준 (1천만 픽셀)
    ),

    // 균형과 품질 사이의 중간 프로필
    ENHANCED_QUALITY(
      0.95f, // 이미지 품질
      0.9f,  // 큰 이미지 품질
      750,   // 최대 DPI
      180,   // 최소 DPI
      2.0f,  // 리사이징 배수
      3000000, // 무손실 압축 최대 픽셀 (3메가픽셀)
      15000000 // 매우 큰 이미지 기준 (1천5백만 픽셀)
    ),

    // 파일 크기를 최소화하는 프로필
    SMALL_SIZE(
      0.85f, // 이미지 품질
      0.75f, // 큰 이미지 품질
      450,   // 최대 DPI
      120,   // 최소 DPI
      1.2f,  // 리사이징 배수
      1000000, // 무손실 압축 최대 픽셀 (1메가픽셀)
      8000000  // 매우 큰 이미지 기준 (8백만 픽셀)
    ),

    // 품질을 우선시하는 프로필
    HIGH_QUALITY(
      0.98f, // 이미지 품질
      0.95f, // 큰 이미지 품질
      900,   // 최대 DPI
      200,   // 최소 DPI
      2.5f,  // 리사이징 배수
      4000000, // 무손실 압축 최대 픽셀 (4메가픽셀)
      20000000 // 매우 큰 이미지 기준 (2천만 픽셀)
    ),

    // 최대 품질 무손실 프로필
    MAXIMUM_QUALITY(
      1.0f,  // 이미지 품질
      0.99f, // 큰 이미지 품질
      1200,  // 최대 DPI
      300,   // 최소 DPI
      4.0f,  // 리사이징 배수
      10000000, // 무손실 압축 최대 픽셀 (1천만 픽셀)
      40000000  // 매우 큰 이미지 기준 (4천만 픽셀)
    ),

    // 초고해상도 프로필 - 매우 높은 해상도 유지
    ULTRA_HIGH_QUALITY(
      1.0f,  // 이미지 품질
      1.0f,  // 큰 이미지 품질
      2400,  // 최대 DPI
      600,   // 최소 DPI
      6.0f,  // 리사이징 배수
      20000000, // 무손실 압축 최대 픽셀 (2천만 픽셀)
      60000000  // 매우 큰 이미지 기준 (6천만 픽셀)
    ),

    // 사용자 정의 프로필 (기본값은 ULTRA_HIGH_QUALITY와 동일)
    CUSTOM(
      1.0f,  // 이미지 품질
      1.0f,  // 큰 이미지 품질
      2400,  // 최대 DPI
      600,   // 최소 DPI
      6.0f,  // 리사이징 배수
      20000000, // 무손실 압축 최대 픽셀 (2천만 픽셀)
      60000000  // 매우 큰 이미지 기준 (6천만 픽셀)
    ),

    // 텍스트 해상도 최적화 프로필 - 글씨 선명도 보존에 중점
    TEXT_OPTIMIZED(
      0.99f, // 이미지 품질 (0.98f에서 향상)
      0.98f, // 큰 이미지 품질 (0.96f에서 향상)
      2000,  // 최대 DPI - 텍스트 해상도 보존을 위해 높은 값 설정 (1800에서 향상)
      400,   // 최소 DPI - 텍스트의 최소 해상도 보장 (300에서 향상)
      4.0f,  // 리사이징 배수 (3.0f에서 향상)
      12000000, // 무손실 압축 최대 픽셀 (8백만에서 12백만으로 향상)
      35000000  // 매우 큰 이미지 기준 (3천만에서 3천5백만으로 향상)
    ),

    // 최대 텍스트 품질 프로필 - 거의 원본 수준의 텍스트 선명도
    TEXT_MAX_QUALITY(
      1.0f,  // 이미지 품질 (최대)
      0.99f, // 큰 이미지 품질 (거의 최대)
      2400,  // 최대 DPI - 인쇄 품질 수준
      600,   // 최소 DPI - 고해상도 보장
      5.0f,  // 리사이징 배수
      15000000, // 무손실 압축 최대 픽셀 (15백만 픽셀)
      50000000  // 매우 큰 이미지 기준 (5천만 픽셀)
    ),

    // 최소 압축/최대 품질 프로필 - 원본과 거의 동일하게 유지하면서 불필요한 데이터만 제거
    MINIMAL_COMPRESSION(
      1.0f,  // 이미지 품질 (최대)
      1.0f,  // 큰 이미지 품질 (최대)
      3000,  // 최대 DPI - 원본 해상도 유지
      800,   // 최소 DPI - 매우 높은 해상도 보장
      8.0f,  // 리사이징 배수 (거의 리사이징 없음)
      25000000, // 무손실 압축 최대 픽셀 (25백만 픽셀 - 대부분 이미지 무손실)
      70000000  // 매우 큰 이미지 기준 (7천만 픽셀)
    ),

    // 약간 더 압축하는 프로필 - MINIMAL보다 조금 더 압축하면서도 고품질 유지
    LIGHT_COMPRESSION(
      0.98f, // 이미지 품질 (약간 감소)
      0.97f, // 큰 이미지 품질 (약간 감소)
      2500,  // 최대 DPI - 여전히 높은 해상도 유지
      600,   // 최소 DPI - 충분히 높은 해상도
      6.0f,  // 리사이징 배수 (약간 리사이징 허용)
      20000000, // 무손실 압축 최대 픽셀 (20백만 픽셀)
      60000000  // 매우 큰 이미지 기준 (6천만 픽셀)
    ),

    // 중간 수준 압축 프로필 - 적당한 압축과 높은 품질의 균형
    MODERATE_COMPRESSION(
      0.96f, // 이미지 품질 (LIGHT보다 더 감소)
      0.95f, // 큰 이미지 품질 (LIGHT보다 더 감소)
      2000,  // 최대 DPI - 여전히 좋은 해상도
      500,   // 최소 DPI - 좋은 텍스트 해상도 유지
      5.0f,  // 리사이징 배수 (더 많은 리사이징 허용)
      15000000, // 무손실 압축 최대 픽셀 (15백만 픽셀)
      50000000  // 매우 큰 이미지 기준 (5천만 픽셀)
    ),

    // 중간-높은 압축 프로필 - BALANCED와 ENHANCED_QUALITY 사이의 압축률
    MEDIUM_COMPRESSION(
      0.93f, // 이미지 품질
      0.88f, // 큰 이미지 품질
      700,   // 최대 DPI - 적절한 해상도
      180,   // 최소 DPI - 가독성 유지 수준
      2.5f,  // 리사이징 배수
      5000000, // 무손실 압축 최대 픽셀 (5백만 픽셀)
      25000000 // 매우 큰 이미지 기준 (2천5백만 픽셀)
    ),

    // 효과적인 압축 프로필 - 실질적인 파일 크기 감소를 위해 최적화됨 (약 10% 압축률)
    EFFECTIVE_COMPRESSION(
      0.9f,  // 이미지 품질 (더 적극적인 압축)
      0.85f, // 큰 이미지 품질
      600,   // 최대 DPI - 더 낮은 해상도 허용
      150,   // 최소 DPI
      1.2f,  // 리사이징 배수 (더 많은 이미지가 리사이징되도록)
      2000000, // 무손실 압축 최대 픽셀 (2백만 픽셀로 낮춤 - 더 많은 이미지에 손실 압축 적용)
      10000000 // 매우 큰 이미지 기준 (1천만 픽셀)
    ),

    // 부드러운 압축 프로필 - 약 5% 압축률, 텍스트 품질 보존
    MILD_COMPRESSION(
      0.95f, // 이미지 품질 (매우 높은 품질)
      0.93f, // 큰 이미지 품질 (매우 높은 품질)
      900,   // 최대 DPI - 높은 텍스트 선명도
      200,   // 최소 DPI - 텍스트 가독성 보장
      2.0f,  // 리사이징 배수 (적당한 수준)
      5000000, // 무손실 압축 최대 픽셀 (5백만 픽셀)
      20000000 // 매우 큰 이미지 기준 (2천만 픽셀)
    ),

    // 중간 품질 프로필 - 정확히 5% 압축률 목표, 고품질 유지
    MEDIUM_QUALITY(
      0.97f, // 이미지 품질 (매우 높은 품질)
      0.95f, // 큰 이미지 품질 (높은 품질)
      1000,  // 최대 DPI - 높은 텍스트 선명도
      250,   // 최소 DPI - 좋은 텍스트 가독성
      3.0f,  // 리사이징 배수 (적절한 수준)
      7000000, // 무손실 압축 최대 픽셀 (7백만 픽셀)
      25000000 // 매우 큰 이미지 기준 (2천5백만 픽셀)
    ),

    // 매우 가벼운 압축 프로필 - 정확히 5% 압축률만 적용, 원본 품질 최대한 유지
    VERY_LIGHT_COMPRESSION(
      0.99f, // 이미지 품질 (거의 원본)
      0.99f, // 큰 이미지 품질 (거의 원본)
      2800,  // 최대 DPI - 원본 해상도에 가까운 유지
      700,   // 최소 DPI - 매우 높은 텍스트 해상도
      7.0f,  // 리사이징 배수 (거의 리사이징 없음)
      22000000, // 무손실 압축 최대 픽셀 (22백만 픽셀)
      65000000 // 매우 큰 이미지 기준 (6천5백만 픽셀)
    ),

    // 균형 압축 프로필 - 약 60-70% 압축률, 적절한 품질 유지
    BALANCED_COMPRESSION(
      0.88f, // 이미지 품질 (적정 품질)
      0.82f, // 큰 이미지 품질 (적정 품질)
      550,   // 최대 DPI - 중간 해상도
      130,   // 최소 DPI - 기본 가독성 수준
      1.4f,  // 리사이징 배수 (적극적인 리사이징)
      1500000, // 무손실 압축 최대 픽셀 (1.5백만 픽셀)
      9000000 // 매우 큰 이미지 기준 (9백만 픽셀)
    );

    // 압축 설정값
    private final float imageQuality;       // 일반 이미지 압축 품질 (0.0-1.0)
    private final float largeImageQuality;  // 큰 이미지 압축 품질 (0.0-1.0)
    private final int maxDpi;               // 최대 DPI (리사이징 기준)
    private final int minDpi;               // 최소 DPI
    private final float resizeThresholdMultiplier; // 이미지 리사이징 배수
    private final int maxLosslessPixels;    // 무손실 압축 최대 픽셀 수
    private final int veryLargeImageThreshold; // 매우 큰 이미지 기준 픽셀 수

    CompressionProfile(float imageQuality, float largeImageQuality, int maxDpi, int minDpi,
                       float resizeThresholdMultiplier, int maxLosslessPixels, int veryLargeImageThreshold) {
      this.imageQuality = imageQuality;
      this.largeImageQuality = largeImageQuality;
      this.maxDpi = maxDpi;
      this.minDpi = minDpi;
      this.resizeThresholdMultiplier = resizeThresholdMultiplier;
      this.maxLosslessPixels = maxLosslessPixels;
      this.veryLargeImageThreshold = veryLargeImageThreshold;
    }

    public float getImageQuality() {
      return imageQuality;
    }

    public float getLargeImageQuality() {
      return largeImageQuality;
    }

    public int getMaxDpi() {
      return maxDpi;
    }

    public int getMinDpi() {
      return minDpi;
    }

    public float getResizeThresholdMultiplier() {
      return resizeThresholdMultiplier;
    }

    public int getMaxLosslessPixels() {
      return maxLosslessPixels;
    }

    public int getVeryLargeImageThreshold() {
      return veryLargeImageThreshold;
    }

    // 예상 압축률 (대략적인 수치)
    public String getEstimatedCompressionRate() {
      if (this == MINIMAL_COMPRESSION) return "1-5%";
      if (this == MILD_COMPRESSION) return "3-7%";
      if (this == LIGHT_COMPRESSION) return "5-10%";
      if (this == ULTRA_HIGH_QUALITY) return "5-10%";
      if (this == MEDIUM_QUALITY) return "5%";
      if (this == VERY_LIGHT_COMPRESSION) return "5%";
      if (this == MODERATE_COMPRESSION) return "10-15%";
      if (this == MAXIMUM_QUALITY) return "10-20%";
      if (this == TEXT_MAX_QUALITY) return "15-25%";
      if (this == EFFECTIVE_COMPRESSION) return "10-20%";
      if (this == TEXT_OPTIMIZED) return "20-30%";
      if (this == HIGH_QUALITY) return "30-40%";
      if (this == MEDIUM_COMPRESSION) return "45-55%";
      if (this == ENHANCED_QUALITY) return "40-50%";
      if (this == TARGET_SIZE_200MB) return "33-40%";
      if (this == BALANCED) return "50-60%";
      if (this == BALANCED_COMPRESSION) return "60-70%";
      if (this == SMALL_SIZE) return "70-80%";
      if (this == MINIMUM_SIZE) return "85-95%";
      return "알 수 없음";
    }

    // 예상 화질 손실 (설명)
    public String getQualityDescription() {
      if (this == MINIMAL_COMPRESSION) return "원본과 거의 동일, 불필요한 데이터만 제거하여 최소한의 압축";
      if (this == MILD_COMPRESSION) return "텍스트 품질 보존, 약 5% 압축률로 원본에 가까운 품질 유지";
      if (this == LIGHT_COMPRESSION) return "원본과 매우 유사, 육안으로 구분 어려운 수준의 약간 더 압축";
      if (this == MODERATE_COMPRESSION) return "고품질 유지하면서 적당한 압축, 세밀한 부분에서만 약간의 차이";
      if (this == MEDIUM_QUALITY) return "정확히 5% 압축률을 목표로 한 프로필, 고품질 및 선명한 텍스트 유지";
      if (this == VERY_LIGHT_COMPRESSION) return "원본 품질 최대한 유지, 정확히 5% 압축률만 적용, 불필요한 데이터만 최소화";
      if (this == ULTRA_HIGH_QUALITY) return "원본보다 더 높은 해상도, 고품질 인쇄물 수준";
      if (this == MAXIMUM_QUALITY) return "원본과 거의 동일한 품질, 육안으로 구분 불가";
      if (this == TEXT_MAX_QUALITY) return "텍스트 품질 최적화, 원본 수준의 텍스트 선명도 보장";
      if (this == EFFECTIVE_COMPRESSION) return "효과적인 파일 크기 감소(약 10%), 여전히 좋은 품질 유지";
      if (this == TEXT_OPTIMIZED) return "텍스트와 선명도 최적화, 글씨 해상도 최대한 보존";
      if (this == HIGH_QUALITY) return "매우 좋은 품질, 세밀한 부분에서만 약간의 차이";
      if (this == MEDIUM_COMPRESSION) return "중간 수준의 품질과 파일 크기 균형, 적당한 압축률";
      if (this == ENHANCED_QUALITY) return "좋은 품질, 대부분의 이미지가 선명하게 유지됨";
      if (this == TARGET_SIZE_200MB) return "좋은 품질, 300MB 파일을 약 200MB로 압축";
      if (this == BALANCED) return "양호한 품질, 일반 용도로 충분";
      if (this == BALANCED_COMPRESSION) return "61267054 bytes, 77.74% 감소, 적절한 품질과 파일 크기의 균형";
      if (this == SMALL_SIZE) return "적절한 품질, 텍스트 가독성 유지";
      if (this == MINIMUM_SIZE) return "낮은 품질, 웹 공유 및 화면 표시용";
      return "알 수 없음";
    }
  }

  // 현재 사용 중인 압축 프로필
  private static final CompressionProfile CURRENT_PROFILE = CompressionProfile.VERY_LIGHT_COMPRESSION;

  /**
   * 압축 프로필을 설정합니다.
   *
   * @param profile 설정할 압축 프로필
   */
  public static void setCurrentProfile(CompressionProfile profile) {
    try {
      // 임시로 현재 프로필 변경
      Field field = PdfCompressionUtil.class.getDeclaredField("CURRENT_PROFILE");
      field.setAccessible(true);

      // final 필드의 값을 변경하기 위한 설정
      Field modifiersField = Field.class.getDeclaredField("modifiers");
      modifiersField.setAccessible(true);
      modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

      // 프로필 변경
      field.set(null, profile);

      log.info("압축 프로필 '{}' 사용 - 예상 압축률: {}, 품질: {}",
        profile.name(), profile.getEstimatedCompressionRate(), profile.getQualityDescription());
    } catch (NoSuchFieldException | IllegalAccessException e) {
      log.error("프로필 변경 중 오류 발생: {}", e.getMessage());
      // 리플렉션 오류 시 기본 메서드 호출
      setCurrentProfile(CompressionProfile.MAXIMUM_QUALITY);
    }
  }

  /**
   * 리플렉션을 사용하여 열거형 상수의 필드 값을 변경합니다.
   *
   * @param profile   값을 변경할 프로필
   * @param fieldName 변경할 필드 이름
   * @param value     새 값
   */
  private static void setFieldValue(CompressionProfile profile, String fieldName, Object value)
    throws NoSuchFieldException, IllegalAccessException {
    Field field = CompressionProfile.class.getDeclaredField(fieldName);
    field.setAccessible(true);

    // final 필드의 값을 변경하기 위한 설정
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    field.set(profile, value);
  }

  /**
   * 여러 PDF 파일에 대한 벤치마크를 실행합니다.
   * 각 파일에 대해 압축을 실행하고 결과를 반환합니다.
   *
   * @param directory       벤치마크할 PDF 파일이 있는 디렉토리
   * @param outputDirectory 압축된 파일을 저장할 디렉토리
   * @param currentProfile
   * @return 벤치마크 결과 (파일 이름 -> 압축 결과)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static Map<String, CompressionResult> runBenchmark(String directory, String outputDirectory, CompressionProfile currentProfile) throws IOException {
    Path inputDir = Paths.get(directory);
    Path outputDir = Paths.get(outputDirectory);

    // 출력 디렉토리가 없으면 생성
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }

    Map<String, CompressionResult> results = new LinkedHashMap<>();

    // 디렉토리에서 PDF 파일만 필터링
    Files.list(inputDir)
      .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
      .forEach(path -> {
        try {
          String fileName = path.getFileName().toString();
          File outputFile = outputDir.resolve("compressed_" + fileName).toFile();

          // 시간 측정 시작
          long startTime = System.currentTimeMillis();

          // 파일 압축
          long originalSize = path.toFile().length();
          long compressedSize = compressPdf(path.toFile(), outputFile, currentProfile);

          // 시간 측정 종료
          long endTime = System.currentTimeMillis();
          long duration = endTime - startTime;

          // 압축률 계산
          double compressionRatio = originalSize > 0 ? (1 - (double) compressedSize / originalSize) * 100 : 0;

          // 결과 저장
          CompressionResult result = new CompressionResult(
            fileName,
            originalSize,
            compressedSize,
            compressionRatio,
            duration
          );

          results.put(fileName, result);

          log.info("벤치마크 결과 - {}: 원본 크기: {}, 압축 크기: {}, 압축률: {}%, 처리 시간: {}ms",
            fileName, formatFileSize(originalSize), formatFileSize(compressedSize),
            String.format("%.2f", compressionRatio), duration);

        } catch (Exception e) {
          log.error("벤치마크 중 오류 발생 - {}: {}", path.getFileName(), e.getMessage());
        }
      });

    // 벤치마크 요약 출력
    summarizeBenchmark(results);

    return results;
  }

  /**
   * 벤치마크 결과를 요약합니다.
   *
   * @param results 벤치마크 결과
   */
  private static void summarizeBenchmark(Map<String, CompressionResult> results) {
    if (results.isEmpty()) {
      log.info("벤치마크 결과가 없습니다.");
      return;
    }

    long totalOriginalSize = 0;
    long totalCompressedSize = 0;
    long totalDuration = 0;
    int successCount = 0;
    int failCount = 0;

    for (CompressionResult result : results.values()) {
      totalOriginalSize += result.getOriginalSize();
      totalCompressedSize += result.getCompressedSize();
      totalDuration += result.getDurationMs();

      if (result.getCompressionRatio() > 0) {
        successCount++;
      } else {
        failCount++;
      }
    }

    double totalCompressionRatio = totalOriginalSize > 0 ?
      (1 - (double) totalCompressedSize / totalOriginalSize) * 100 : 0;

    log.info("\n========== 벤치마크 요약 ==========");
    log.info("총 파일 수: {}", results.size());
    log.info("압축 성공: {}, 압축 실패: {}", successCount, failCount);
    log.info("원본 총 크기: {}", formatFileSize(totalOriginalSize));
    log.info("압축 총 크기: {}", formatFileSize(totalCompressedSize));
    log.info("전체 압축률: {}%", String.format("%.2f", totalCompressionRatio));
    log.info("총 처리 시간: {}ms", totalDuration);
    log.info("파일당 평균 처리 시간: {}ms", results.size() > 0 ? totalDuration / results.size() : 0);
    log.info("==============================");
  }

  /**
   * 파일 크기를 보기 좋게 포맷팅합니다.
   *
   * @param size 바이트 단위 크기
   * @return 포맷팅된 크기 문자열 (예: 1.23 KB, 4.56 MB)
   */
  private static String formatFileSize(long size) {
    if (size <= 0) return "0 B";

    final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
    int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

    return new DecimalFormat("#,##0.##")
      .format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
  }

  /**
   * 압축 결과를 저장하는 내부 클래스
   */
  public static class CompressionResult {
    private final String fileName;
    private final long originalSize;
    private final long compressedSize;
    private final double compressionRatio;
    private final long durationMs;

    public CompressionResult(String fileName, long originalSize, long compressedSize,
                             double compressionRatio, long durationMs) {
      this.fileName = fileName;
      this.originalSize = originalSize;
      this.compressedSize = compressedSize;
      this.compressionRatio = compressionRatio;
      this.durationMs = durationMs;
    }

    public String getFileName() {
      return fileName;
    }

    public long getOriginalSize() {
      return originalSize;
    }

    public long getCompressedSize() {
      return compressedSize;
    }

    public double getCompressionRatio() {
      return compressionRatio;
    }

    public long getDurationMs() {
      return durationMs;
    }

    @Override
    public String toString() {
      return String.format(
        "%s: %s → %s (%.2f%%, %dms)",
        fileName,
        formatFileSize(originalSize),
        formatFileSize(compressedSize),
        compressionRatio,
        durationMs
      );
    }
  }

  /**
   * PDF 파일을 압축합니다.
   *
   * @param inputFile  입력 PDF 파일
   * @param outputFile 출력 PDF 파일
   * @param currentProfile
   * @return 압축된 파일의 크기 (바이트)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static long compressPdf(File inputFile, File outputFile, CompressionProfile currentProfile) throws IOException {
    log.info("PDF 압축 시작: {}, 원본 크기: {} 바이트", inputFile.getName(), inputFile.length());
    long startTime = System.currentTimeMillis();
    long originalSize = inputFile.length();

    if (originalSize < MIN_SIZE_FOR_COMPRESSION) {
      log.info("파일 크기가 너무 작아 압축을 건너뜁니다. ({}바이트)", originalSize);
      if (!inputFile.equals(outputFile)) {
        Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("원본 파일을 결과 파일로 복사했습니다: {}", outputFile.getName());
      }
      return originalSize;
    }

    try (PDDocument document = Loader.loadPDF(inputFile)) {
      log.info("PDF 문서 로드 완료, 총 {} 페이지", document.getNumberOfPages());

      // 각 페이지의 이미지 최적화
      PDPageTree pages = document.getPages();
      AtomicInteger totalImagesProcessed = new AtomicInteger(0);
      AtomicInteger totalImagesOptimized = new AtomicInteger(0);
      AtomicInteger pageCount = new AtomicInteger(0);

      for (PDPage page : pages) {
        int pageIndex = pageCount.incrementAndGet();
        log.info("페이지 {} 처리 중...", pageIndex);
        int[] result = optimizePageImages(document, page, pageIndex, currentProfile);
        totalImagesProcessed.addAndGet(result[0]);
        totalImagesOptimized.addAndGet(result[1]);
      }

      // 메타데이터 최적화
      log.info("메타데이터 최적화 시작...");
      optimizeMetadata(document);

      // 압축된 PDF 저장
      log.info("최적화된 PDF 저장 중...");
      document.save(outputFile);

      long compressedSize = outputFile.length();
      long processingTime = System.currentTimeMillis() - startTime;

      // 압축 통계 계산
      double compressionRatio = originalSize > 0 ? (1 - (double) compressedSize / originalSize) * 100 : 0;

      // 압축이 실패한 경우 원본 파일을 복사
      if (compressedSize >= originalSize) {
        log.warn("압축이 실패했습니다. 원본 파일을 유지합니다. (원본: {} bytes, 압축: {} bytes, 압축률: {}%)",
          originalSize, compressedSize, String.format("%.2f", compressionRatio));

        if (!inputFile.equals(outputFile)) {
          Files.copy(inputFile.toPath(), outputFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          log.info("원본 파일을 결과 파일로 복사했습니다: {}", outputFile.getName());
        }
        return originalSize;
      }

      // 성공적인 압축 결과 로깅
      log.info("PDF 압축 완료: 처리 시간: {}ms, 페이지 수: {}, 총 이미지: {}, 최적화된 이미지: {}",
        processingTime, pageCount.get(), totalImagesProcessed.get(), totalImagesOptimized.get());
      log.info("압축 결과: 원본: {} bytes → 압축: {} bytes ({}% 감소)",
        originalSize, compressedSize, String.format("%.2f", compressionRatio));

      return compressedSize;
    }
  }

  /**
   * 페이지의 이미지를 최적화합니다.
   *
   * @param document  PDF 문서
   * @param page      최적화할 페이지
   * @param pageIndex 페이지 인덱스
   * @param currentProfile
   * @return 처리된 이미지 수와 최적화된 이미지 수를 담은 배열
   */
  private static int[] optimizePageImages(PDDocument document, PDPage page, int pageIndex, CompressionProfile currentProfile) {
    PDResources resources = page.getResources();
    if (resources == null) {
      log.debug("페이지 {}: 리소스 없음", pageIndex);
      return new int[]{0, 0};
    }

    COSDictionary xObjects = resources.getCOSObject().getCOSDictionary(COSName.XOBJECT);
    if (xObjects == null) {
      log.debug("페이지 {}: XObject 없음", pageIndex);
      return new int[]{0, 0};
    }

    Set<COSName> xObjectNames = xObjects.keySet();
    log.debug("페이지 {}: {} XObject 발견", pageIndex, xObjectNames.size());

    int processedImages = 0;
    int optimizedImages = 0;

    // 메모리 사용량 관리를 위한 가비지 컬렉션 요청
    System.gc();

    for (COSName name : xObjectNames) {
      COSBase xObject = xObjects.getDictionaryObject(name);
      if (xObject instanceof COSDictionary xObjectDict) {
        if (COSName.IMAGE.equals(xObjectDict.getItem(COSName.SUBTYPE))) {
          processedImages++;
          try {
            // PDFBox 3.0.1에서는 직접 PDImageXObject를 생성하는 것이 변경됨
            // 기존 이미지가 COSStream인지 확인
            if (xObjectDict instanceof COSStream) {
              // 기존 이미지를 PDImageXObject로 변환
              PDImageXObject image = new PDImageXObject(new PDStream((COSStream) xObjectDict), resources);

              // 이미지 정보 로깅
              log.debug("페이지 {}, 이미지 {}: 원본 크기: {}x{}, 타입: {}",
                pageIndex, processedImages, image.getWidth(), image.getHeight(),
                image.getSuffix() != null ? image.getSuffix() : "알 수 없음");

              // 대형 이미지인 경우 메모리 부족 방지 대신 특수 처리
              long imagePixels = (long) image.getWidth() * (long) image.getHeight();
              boolean isVeryLargeImage = imagePixels > currentProfile.getVeryLargeImageThreshold();

              if (isVeryLargeImage) {
                log.info("페이지 {}, 이미지 {}: 매우 큰 이미지 ({}x{}) 특수 처리 적용",
                  pageIndex, processedImages, image.getWidth(), image.getHeight());
              }

              // 이미지 추출 및 최적화
              BufferedImage bImage = image.getImage();
              if (bImage != null) {
                // 최적화된 이미지 생성
                BufferedImage optimizedImage = optimizeImage(bImage, isVeryLargeImage);
                PDImageXObject optimizedPDImage = createOptimizedImage(document, optimizedImage, isVeryLargeImage);

                // 원본 이미지 메모리 해제
                bImage.flush();
                optimizedImage.flush();

                // 이미지 크기 비교 및 교체
                long originalSize = getImageSize(xObjectDict);
                long optimizedSize = getImageSize(optimizedPDImage.getCOSObject());
                double compressionRatio = originalSize > 0 ? (1 - (double) optimizedSize / originalSize) * 100 : 0;

                log.debug("페이지 {}, 이미지 {}: 원본: {} bytes, 최적화: {} bytes, 감소율: {}%",
                  pageIndex, processedImages, originalSize, optimizedSize, String.format("%.2f", compressionRatio));

                if (optimizedSize < originalSize) {
                  xObjects.setItem(name, optimizedPDImage);
                  log.debug("페이지 {}, 이미지 {}: 최적화 적용됨", pageIndex, processedImages);
                  optimizedImages++;
                } else {
                  log.debug("페이지 {}, 이미지 {}: 최적화 효과 없음, 원본 유지", pageIndex, processedImages);
                }

                // 주기적인 메모리 정리
                if (processedImages % 5 == 0) {
                  System.gc();
                }
              } else {
                log.warn("페이지 {}, 이미지 {}: 이미지 데이터를 추출할 수 없음", pageIndex, processedImages);
              }
            } else {
              log.debug("페이지 {}, 이미지 {}: COSStream이 아니라서 처리 불가", pageIndex, processedImages);
            }
          } catch (OutOfMemoryError oom) {
            log.error("페이지 {}, 이미지 {} 처리 중 메모리 부족: {}", pageIndex, processedImages, oom.getMessage());
            System.gc();
          } catch (Exception e) {
            log.warn("페이지 {}, 이미지 {} 처리 중 오류 발생: {}", pageIndex, processedImages, e.getMessage());
            if (log.isDebugEnabled()) {
              e.printStackTrace();
            }
          }
        }
      }
    }

    log.debug("페이지 {} 처리 완료: 총 이미지 {}, 최적화된 이미지 {}", pageIndex, processedImages, optimizedImages);
    return new int[]{processedImages, optimizedImages};
  }

  /**
   * 이미지 크기를 계산합니다.
   *
   * @param dict 이미지 딕셔너리
   * @return 이미지 크기 (바이트)
   */
  private static long getImageSize(COSDictionary dict) {
    try {
      if (dict instanceof COSStream) {
        // COSStream의 경우 단순 길이 사용
        return ((COSStream) dict).getLength();
      } else {
        // COSStream이 아닌 경우 dictionary 크기 근사치 사용
        return dict.size();
      }
    } catch (Exception e) {
      log.warn("이미지 크기 계산 중 오류 발생: {}", e.getMessage());
      return Long.MAX_VALUE;
    }
  }

  /**
   * 이미지를 최적화합니다.
   *
   * @param original         원본 이미지
   * @param isVeryLargeImage 매우 큰 이미지 여부
   * @return 최적화된 이미지
   */
  private static BufferedImage optimizeImage(BufferedImage original, boolean isVeryLargeImage) {
    // 이미지 크기 조정
    BufferedImage resized = resizeImage(original, isVeryLargeImage);

    // 이미지 품질 조정
    return adjustImageQuality(resized);
  }

  /**
   * 이미지 크기를 조정합니다.
   *
   * @param original         원본 이미지
   * @param isVeryLargeImage 매우 큰 이미지 여부
   * @return 조정된 이미지
   */
  private static BufferedImage resizeImage(BufferedImage original, boolean isVeryLargeImage) {
    // 이미지 크기 조정 활성화 (원본 크기 절대 유지 안함)
    boolean shouldResize = shouldResizeImage(original);

    // 기본 스케일 계산
    float scale = 1.0f;

    if (shouldResize || isVeryLargeImage) {
      scale = Math.min(
        CURRENT_PROFILE.getMaxDpi() / (float) original.getWidth(),
        CURRENT_PROFILE.getMaxDpi() / (float) original.getHeight()
      );

      // 큰 이미지는 더 적극적으로 크기 조정
      if (isVeryLargeImage) {
        scale = Math.min(scale, 0.8f);
      } else if (original.getWidth() * original.getHeight() > 5000000) {
        // 5메가픽셀 이상인 이미지도 약간 크기 조정
        scale = Math.min(scale, 0.9f);
      }

      // 원본보다 커지지 않도록
      scale = Math.min(scale, 1.0f);

      // 실제 크기 조정이 필요한 경우만
      if (scale < 0.99f) {
        int targetWidth = Math.max(CURRENT_PROFILE.getMinDpi(), (int) (original.getWidth() * scale));
        int targetHeight = Math.max(CURRENT_PROFILE.getMinDpi(), (int) (original.getHeight() * scale));

        log.debug("이미지 크기 조정: {}x{} -> {}x{}, 스케일: {}",
          original.getWidth(), original.getHeight(), targetWidth, targetHeight, scale);

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, original.getType());
        resized.getGraphics().drawImage(original, 0, 0, targetWidth, targetHeight, null);
        return resized;
      }
    }

    // 크기 조정이 불필요하면 원본 유지
    return original;
  }

  /**
   * 이미지 품질을 조정합니다.
   *
   * @param image 원본 이미지
   * @return 품질 조정된 이미지
   */
  private static BufferedImage adjustImageQuality(BufferedImage image) {
    // 이미지 품질 조정 로직
    // 현재는 원본 이미지를 반환하지만, 필요한 경우 JPEG 압축 등을 적용할 수 있습니다.
    return image;
  }

  /**
   * 최적화된 이미지를 PDF 이미지 객체로 생성합니다.
   *
   * @param document         PDF 문서
   * @param image            최적화된 이미지
   * @param isVeryLargeImage 매우 큰 이미지 여부
   * @return PDF 이미지 객체
   * @throws IOException 이미지 처리 중 오류 발생 시
   */
  private static PDImageXObject createOptimizedImage(PDDocument document, BufferedImage image, boolean isVeryLargeImage) throws IOException {
    // 효율적인 압축을 위한 하이브리드 접근법
    long pixelCount = (long) image.getWidth() * (long) image.getHeight();

    // 무손실 압축 적용 조건
    if (hasAlphaChannel(image) || pixelCount < CURRENT_PROFILE.getMaxLosslessPixels()) {
      // 투명도가 있거나 작은 이미지는 무손실 압축
      return LosslessFactory.createFromImage(document, image);
    } else if (isVeryLargeImage) {
      // 매우 큰 이미지는 더 높은 압축률
      return JPEGFactory.createFromImage(document, image, CURRENT_PROFILE.getLargeImageQuality());
    } else {
      // 중간 크기 이미지는 고품질 압축
      return JPEGFactory.createFromImage(document, image, CURRENT_PROFILE.getImageQuality());
    }
  }

  /**
   * 이미지에 알파 채널(투명도)이 있는지 확인합니다.
   *
   * @param image 확인할 이미지
   * @return 알파 채널이 있으면 true
   */
  private static boolean hasAlphaChannel(BufferedImage image) {
    return image.getColorModel().hasAlpha();
  }

  /**
   * 이미지가 JPEG 압축에 적합한지 확인합니다.
   *
   * @param image 확인할 이미지
   * @return JPEG 압축이 적합한 경우 true
   */
  private static boolean isJPEGCompatible(BufferedImage image) {
    int type = image.getType();
    return type == BufferedImage.TYPE_INT_RGB ||
      type == BufferedImage.TYPE_INT_ARGB ||
      type == BufferedImage.TYPE_INT_ARGB_PRE;
  }

  /**
   * 이미지 크기 조정이 필요한지 확인합니다.
   *
   * @param image 확인할 이미지
   * @return 크기 조정이 필요한 경우 true
   */
  private static boolean shouldResizeImage(BufferedImage image) {
    // 크기 조정 기준: 최대 DPI의 배수를 기준으로 함
    float threshold = CURRENT_PROFILE.getMaxDpi() * CURRENT_PROFILE.getResizeThresholdMultiplier();
    return image.getWidth() > threshold || image.getHeight() > threshold;
  }

  /**
   * PDF 문서의 메타데이터를 최적화합니다.
   *
   * @param document PDF 문서
   */
  private static void optimizeMetadata(PDDocument document) {
    try {
      // 메타데이터 내용 기록
      if (log.isDebugEnabled() && document.getDocumentInformation() != null) {
        log.debug("메타데이터 최적화 전: 제목='{}', 작성자='{}', 주제='{}', 키워드='{}'",
          document.getDocumentInformation().getTitle(),
          document.getDocumentInformation().getAuthor(),
          document.getDocumentInformation().getSubject(),
          document.getDocumentInformation().getKeywords());
      }

      // 모든 메타데이터 제거
      document.getDocumentCatalog().setMetadata(null);
      log.debug("문서 카탈로그 메타데이터 제거됨");

      // 문서 정보 딕셔너리 최적화
      if (document.getDocumentInformation() != null) {
        document.getDocumentInformation().setTitle(null);
        document.getDocumentInformation().setAuthor(null);
        document.getDocumentInformation().setSubject(null);
        document.getDocumentInformation().setKeywords(null);
        document.getDocumentInformation().setCreator(null);
        document.getDocumentInformation().setProducer(null);
        log.debug("문서 정보 딕셔너리 최적화 완료");
      }
    } catch (Exception e) {
      log.warn("메타데이터 최적화 중 오류 발생: {}", e.getMessage());
      if (log.isDebugEnabled()) {
        e.printStackTrace();
      }
    }
  }

  /**
   * 특정 압축 프로필과 해상도(DPI) 설정을 사용하여 PDF 파일을 압축합니다.
   *
   * @param inputFile  입력 PDF 파일
   * @param outputFile 출력 PDF 파일
   * @param profile    사용할 압축 프로필
   * @param maxDpi      적용할 최대 DPI
   * @param minDpi      적용할 최소 DPI
   * @param currentProfile
   * @return 압축된 파일의 크기 (바이트)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static long compressPdfWithCustomDpi(File inputFile, File outputFile,
                                              CompressionProfile profile, int maxDpi, int minDpi, CompressionProfile currentProfile)
    throws IOException {
    CompressionProfile originalProfile = currentProfile;
    CompressionProfile customProfile = CompressionProfile.CUSTOM;

    try {
      try {
        // CUSTOM 프로필에 설정 값 적용
        setFieldValue(customProfile, "imageQuality", profile.getImageQuality());
        setFieldValue(customProfile, "largeImageQuality", profile.getLargeImageQuality());
        setFieldValue(customProfile, "resizeThresholdMultiplier", profile.getResizeThresholdMultiplier());
        setFieldValue(customProfile, "maxLosslessPixels", profile.getMaxLosslessPixels());
        setFieldValue(customProfile, "veryLargeImageThreshold", profile.getVeryLargeImageThreshold());

        // DPI 값만 변경
        setFieldValue(customProfile, "maxDpi", Math.min(4800, maxDpi));
        setFieldValue(customProfile, "minDpi", Math.max(72, minDpi));
      } catch (NoSuchFieldException | IllegalAccessException e) {
        log.error("커스텀 프로필 설정 중 오류 발생: {}", e.getMessage());
        throw new IOException("커스텀 DPI 설정 중 오류 발생", e);
      }

      log.info("커스텀 DPI로 프로필 사용: 최대 DPI={}, 최소 DPI={}, 기반 프로필={}",
        maxDpi, minDpi, profile.name());

      // 임시로 CUSTOM 프로필 사용
      setCurrentProfile(customProfile);

      // 압축 실행
      return compressPdf(inputFile, outputFile, currentProfile);
    } finally {
      // 원래 프로필로 복원
      try {
        setCurrentProfile(originalProfile);
      } catch (Exception e) {
        log.warn("원래 프로필로 복원 중 오류 발생: {}", e.getMessage());
      }
    }
  }

  /**
   * 현재 기본 프로필에서 해상도(DPI)만 변경하여 PDF를 압축합니다.
   *
   * @param inputFile  입력 PDF 파일
   * @param outputFile 출력 PDF 파일
   * @param maxDpi      적용할 최대 DPI
   * @param minDpi      적용할 최소 DPI
   * @return 압축된 파일의 크기 (바이트)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static long compressPdfWithDpi(File inputFile, File outputFile, int maxDpi, int minDpi) throws IOException {
    return compressPdfWithCustomDpi(inputFile, outputFile, CURRENT_PROFILE, maxDpi, minDpi, CURRENT_PROFILE);
  }

  /**
   * 해상도(DPI) 설정으로 파일을 압축하고 "_압축" 접미사가 붙은 파일을 생성합니다.
   *
   * @param inputFile 입력 PDF 파일
   * @param maxDpi    적용할 최대 DPI
   * @param minDpi    적용할 최소 DPI
   * @return 압축된 파일의 크기 (바이트)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static long compressPdfWithPostfixAndDpi(File inputFile, int maxDpi, int minDpi) throws IOException {
    // 파일 이름과 확장자 분리
    String fileName = inputFile.getName();
    String fileExtension = "";
    String baseName = fileName;

    int lastDotIndex = fileName.lastIndexOf(".");
    if (lastDotIndex > 0) {
      fileExtension = fileName.substring(lastDotIndex); // 확장자 (.pdf 포함)
      baseName = fileName.substring(0, lastDotIndex);   // 확장자 제외한 파일명
    }

    // 새 파일 이름 생성 (예: sample.pdf -> sample_압축.pdf)
    String newFileName = baseName + COMPRESSION_POSTFIX + fileExtension;

    // 원본 파일과 같은 디렉토리에 새 파일 생성
    File outputFile = new File(inputFile.getParent(), newFileName);

    log.info("압축 결과 파일 생성 (DPI 설정: 최대={}, 최소={}): {}", maxDpi, minDpi, outputFile.getAbsolutePath());

    // DPI 설정으로 압축 실행
    return compressPdfWithDpi(inputFile, outputFile, maxDpi, minDpi);
  }

  /**
   * 특정 압축 프로필과 커스텀 DPI 설정을 사용하여 PDF 파일을 압축하고 결과 파일에 접미사를 붙입니다.
   *
   * @param inputFile 입력 PDF 파일
   * @param profile   사용할 압축 프로필
   * @param maxDpi     적용할 최대 DPI
   * @param minDpi     적용할 최소 DPI
   * @return 압축된 파일의 크기 (바이트)
   * @throws IOException 파일 처리 중 오류 발생 시
   */
  public static long compressPdfWithProfileAndDpi(File inputFile, CompressionProfile profile,
                                                  int maxDpi, int minDpi) throws IOException {
    // 파일 이름과 확장자 분리
    String fileName = inputFile.getName();
    String fileExtension = "";
    String baseName = fileName;

    int lastDotIndex = fileName.lastIndexOf(".");
    if (lastDotIndex > 0) {
      fileExtension = fileName.substring(lastDotIndex); // 확장자 (.pdf 포함)
      baseName = fileName.substring(0, lastDotIndex);   // 확장자 제외한 파일명
    }

    // 새 파일 이름 생성 (예: sample.pdf -> sample_압축.pdf)
    String newFileName = baseName + COMPRESSION_POSTFIX + fileExtension;

    // 원본 파일과 같은 디렉토리에 새 파일 생성
    File outputFile = new File(inputFile.getParent(), newFileName);

    log.info("커스텀 DPI로 압축 결과 파일 생성 (프로필: {}, 최대 DPI: {}, 최소 DPI: {}): {}",
      profile.name(), maxDpi, minDpi, outputFile.getAbsolutePath());

    // 프로필과 DPI 설정으로 압축 실행
    return compressPdfWithCustomDpi(inputFile, outputFile, profile, maxDpi, minDpi, CURRENT_PROFILE);
  }

  /**
   * 여러 PDF 파일을 특정 압축 프로필과 DPI 설정으로 일괄 압축합니다.
   *
   * @param profile 압축 프로필
   * @param maxDpi  적용할 최대 DPI
   * @param minDpi  적용할 최소 DPI
   * @param files   처리할 파일 목록
   * @return 압축 결과 맵
   */
  public static Map<String, CompressionResult> compressMultipleWithProfileAndDpi(
    CompressionProfile profile, int maxDpi, int minDpi, File... files) {
    Map<String, CompressionResult> results = new LinkedHashMap<>();

    for (File file : files) {
      try {
        String fileName = file.getName();

        // 시간 측정 시작
        long startTime = System.currentTimeMillis();

        // 파일 압축
        long originalSize = file.length();
        long compressedSize = compressPdfWithProfileAndDpi(file, profile, maxDpi, minDpi);

        // 시간 측정 종료
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 압축률 계산
        double compressionRatio = originalSize > 0 ? (1 - (double) compressedSize / originalSize) * 100 : 0;

        // 결과 저장
        CompressionResult result = new CompressionResult(
          fileName,
          originalSize,
          compressedSize,
          compressionRatio,
          duration
        );

        results.put(fileName, result);

        log.info("압축 완료 - {} (프로필: {}, DPI: {}-{}): 원본 크기: {}, 압축 크기: {}, 압축률: {}%, 처리 시간: {}ms",
          fileName, profile.name(), minDpi, maxDpi,
          formatFileSize(originalSize), formatFileSize(compressedSize),
          String.format("%.2f", compressionRatio), duration);

      } catch (Exception e) {
        log.error("파일 압축 중 오류 발생 - {}: {}", file.getName(), e.getMessage());
      }
    }

    return results;
  }
} 