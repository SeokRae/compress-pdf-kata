package com.example.pdf.compress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PdfFileManager 클래스에 대한 테스트
 */
@DisplayName("PDF 파일 관리자 테스트")
public class PdfFileManagerTest {

    private static final String BOOKS_DIR = "src/test/resources/books";
    private static final String SAMPLE_FILE = "소프트웨어 설계의 정석.pdf";
    
    @TempDir
    Path tempDir;
    
    private File samplePdf;
    private File outputDir;
    
    @BeforeEach
    void setUp() {
        // 테스트 PDF 파일 경로 설정
        samplePdf = new File(BOOKS_DIR, SAMPLE_FILE);
        
        // 테스트 출력 디렉토리 설정
        outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        // 테스트 파일 존재 확인
        assertThat(samplePdf)
                .withFailMessage("테스트 PDF 파일이 존재하지 않습니다: %s", samplePdf.getAbsolutePath())
                .exists();
    }
    
    @AfterEach
    void tearDown() {
        // 필요한 경우 추가 정리 작업 수행
    }
    
    @Test
    @DisplayName("PDF 메타데이터 추출 테스트")
    void testExtractMetadata() throws IOException {
        // 테스트 실행
        Map<String, String> metadata = PdfFileManager.extractMetadata(samplePdf);
        
        // 결과 출력
        System.out.println("=== PDF 메타데이터 ===");
        metadata.forEach((key, value) -> System.out.println(key + ": " + value));
        
        // 결과 검증
        assertThat(metadata)
                .withFailMessage("메타데이터 맵이 비어있지 않아야 합니다")
                .isNotEmpty();
        
        assertThat(metadata)
                .withFailMessage("메타데이터에 PageCount가 포함되어야 합니다")
                .containsKey("PageCount");
                
        assertThat(metadata)
                .withFailMessage("메타데이터에 FileSize가 포함되어야 합니다")
                .containsKey("FileSize");
    }
    
    @Test
    @DisplayName("PDF 품질 분석 테스트")
    void testAnalyzeQuality() throws IOException {
        // 테스트 실행
        PdfFileManager.PdfQualityInfo qualityInfo = PdfFileManager.analyzeQuality(samplePdf);
        
        // 결과 출력
        System.out.println("=== PDF 품질 분석 결과 ===");
        System.out.println(qualityInfo);
        
        // 결과 검증
        assertThat(qualityInfo.getFileSize())
                .withFailMessage("파일 크기는 0보다 커야 합니다")
                .isGreaterThan(0);
                
        assertThat(qualityInfo.getPageCount())
                .withFailMessage("페이지 수는 0보다 커야 합니다")
                .isGreaterThan(0);
                
        assertThat(qualityInfo.getEstimatedDpi())
                .withFailMessage("예상 DPI는 0보다 커야 합니다")
                .isGreaterThan(0);
                
        assertThat(qualityInfo.getQualityLevel())
                .withFailMessage("품질 레벨이 '알 수 없음'이 아니어야 합니다")
                .isNotEqualTo("알 수 없음");
    }
    
    @Test
    @DisplayName("PDF 요약 정보 테스트")
    void testGetSummary() throws IOException {
        // 테스트 실행
        String summary = PdfFileManager.getSummary(samplePdf);
        
        // 결과 출력
        System.out.println(summary);
        
        // 결과 검증
        assertThat(summary)
                .withFailMessage("요약 정보에 파일명이 포함되어야 합니다")
                .contains(samplePdf.getName());
                
        assertThat(summary)
                .withFailMessage("요약 정보에 페이지 수가 포함되어야 합니다")
                .contains("페이지 수:");
                
        assertThat(summary)
                .withFailMessage("요약 정보에 품질 레벨이 포함되어야 합니다")
                .contains("품질 레벨:");
    }
    
    @Test
    @DisplayName("워터마크 추가 테스트")
    void testAddTextWatermark() throws IOException {
        // 테스트 출력 파일 설정
        File outputFile = new File(outputDir, "watermarked_" + samplePdf.getName());
        
        // 테스트 실행
        long startTime = System.currentTimeMillis();
        File result = PdfFileManager.addTextWatermark(samplePdf, outputFile, "CONFIDENTIAL", 0.3f);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== 워터마크 추가 결과 ===");
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        System.out.println("출력 파일: " + result.getAbsolutePath());
        
        // 결과 검증
        assertThat(result)
                .withFailMessage("출력 파일이 생성되어야 합니다")
                .exists();
                
        assertThat(result.length())
                .withFailMessage("출력 파일 크기는 0보다 커야 합니다")
                .isGreaterThan(0);
    }
    
    @Test
    @DisplayName("미리보기 이미지 추출 테스트")
    void testExtractPreviewImage() throws IOException {
        // 테스트 실행
        File previewImage = PdfFileManager.extractPreviewImage(samplePdf, outputDir, 150);
        
        // 결과 출력
        System.out.println("=== 미리보기 이미지 추출 결과 ===");
        System.out.println("미리보기 이미지: " + previewImage.getAbsolutePath());
        System.out.println("이미지 크기: " + PdfFileManager.formatFileSize(previewImage.length()));
        
        // 결과 검증
        assertThat(previewImage)
                .withFailMessage("미리보기 이미지가 생성되어야 합니다")
                .exists();
    }
    
    @Test
    @DisplayName("일괄 처리 테스트")
    void testBatchProcess() throws IOException {
        // 테스트 실행
        long startTime = System.currentTimeMillis();
        
        PdfFileManager.batchProcess(
                new File(BOOKS_DIR),
                outputDir,
                (inputFile, outputFile) -> {
                    // 간단한 파일 복사 처리
                    Files.copy(inputFile.toPath(), outputFile.toPath());
                }
        );
        
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== 일괄 처리 결과 ===");
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        
        File[] outputFiles = outputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        System.out.println("처리된 파일 수: " + (outputFiles != null ? outputFiles.length : 0));
        
        // 결과 검증
        assertThat(outputFiles)
                .withFailMessage("출력 디렉토리에 PDF 파일이 하나 이상 있어야 합니다")
                .isNotNull()
                .isNotEmpty();
    }
    
    @Test
    @DisplayName("파일 크기 형식 변환 테스트")
    void testFormatFileSize() {
        // 테스트 데이터
        long[] sizes = {0, 500, 1024, 1500, 1024 * 1024, 1024 * 1024 * 1024, 1024L * 1024L * 1024L * 1024L};
        
        // 결과 출력
        System.out.println("=== 파일 크기 형식 변환 결과 ===");
        for (long size : sizes) {
            String formatted = PdfFileManager.formatFileSize(size);
            System.out.println(size + " bytes = " + formatted);
            
            // 결과 검증
            assertThat(formatted)
                    .withFailMessage("형식 변환된 문자열이 비어있지 않아야 합니다")
                    .isNotEmpty();
        }
    }
    
    @Test
    @DisplayName("PDF 파일을 200MB 이하로 분할 테스트")
    void testSplitPdfBySize() throws IOException {
        // 최대 파일 크기 (200MB)
        int maxSizeMB = 200;
        
        // 출력 디렉토리 설정
        File splitOutputDir = tempDir.resolve("split_by_size").toFile();
        splitOutputDir.mkdirs();
        
        // 테스트 실행
        long startTime = System.currentTimeMillis();
        File[] splitFiles = PdfFileManager.splitPdfBySize(samplePdf, splitOutputDir, maxSizeMB);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== PDF 크기 기반 분할 결과 ===");
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        System.out.println("분할된 파일 수: " + splitFiles.length);
        
        // 각 분할 파일의 크기 확인
        long totalSize = 0;
        for (File file : splitFiles) {
            long fileSize = file.length();
            totalSize += fileSize;
            System.out.println("- " + file.getName() + ": " + PdfFileManager.formatFileSize(fileSize));
            
            // 분할된 파일이 최대 크기 이하인지 검증
            assertThat(fileSize)
                    .withFailMessage("분할된 파일 크기가 최대 크기(" + maxSizeMB + "MB)를 초과합니다: " + file.getName())
                    .isLessThanOrEqualTo(maxSizeMB * 1024L * 1024L);
        }
        
        System.out.println("총 크기: " + PdfFileManager.formatFileSize(totalSize));
        System.out.println("원본 크기: " + PdfFileManager.formatFileSize(samplePdf.length()));
        
        // 결과 검증
        assertThat(splitFiles)
                .withFailMessage("분할된 파일이 하나 이상 생성되어야 합니다")
                .isNotEmpty();
    }
    
    @Test
    @DisplayName("books 디렉토리 내 모든 PDF 파일을 200MB 이하로 분할 테스트")
    void testSplitAllPdfsBySize() throws IOException {
        // 최대 파일 크기 (200MB)
        int maxSizeMB = 200;
        
        // 출력 디렉토리 설정
        File outputRootDir = tempDir.resolve("all_split_by_size").toFile();
        outputRootDir.mkdirs();
        
        // 테스트 실행
        long startTime = System.currentTimeMillis();
        int totalProcessedFiles = PdfFileManager.splitAllPdfsBySize(new File(BOOKS_DIR), outputRootDir, maxSizeMB);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== books 디렉토리 내 PDF 크기 기반 분할 결과 ===");
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        System.out.println("처리된 파일 수: " + totalProcessedFiles);
        
        // 결과 디렉토리 확인
        File[] splitDirs = outputRootDir.listFiles(File::isDirectory);
        if (splitDirs != null) {
            System.out.println("생성된 분할 디렉토리 수: " + splitDirs.length);
            
            // 각 분할 디렉토리 내 파일 확인
            for (File dir : splitDirs) {
                File[] files = dir.listFiles(file -> file.getName().toLowerCase().endsWith(".pdf"));
                int fileCount = files != null ? files.length : 0;
                
                System.out.println("- " + dir.getName() + ": " + fileCount + "개 파일");
                
                // 각 파일의 크기 확인
                if (files != null) {
                    for (File file : files) {
                        long fileSize = file.length();
                        System.out.println("  - " + file.getName() + ": " + PdfFileManager.formatFileSize(fileSize));
                        
                        // 분할된 파일이 최대 크기 이하인지 검증
                        assertThat(fileSize)
                                .withFailMessage("분할된 파일 크기가 최대 크기(" + maxSizeMB + "MB)를 초과합니다: " + file.getName())
                                .isLessThanOrEqualTo(maxSizeMB * 1024L * 1024L);
                    }
                }
            }
        }
        
        // 결과 검증
        assertThat(totalProcessedFiles)
                .withFailMessage("하나 이상의 파일이 처리되어야 합니다")
                .isGreaterThan(0);
    }
} 