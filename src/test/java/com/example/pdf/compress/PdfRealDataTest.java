package com.example.pdf.compress;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 데이터를 사용한 PDF 처리 테스트
 * 이 테스트는 실제 파일 경로를 사용하여 PDF 파일 처리 기능을 검증합니다.
 */
@DisplayName("PDF 실제 데이터 처리 테스트")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PdfRealDataTest {

    // 실제 데이터 경로 설정
    private static final String BOOKS_DIR = "src/test/resources/books";
    private static final String REAL_OUTPUT_DIR = "target/real-test-results";
    private static final String REAL_SPLIT_OUTPUT_DIR = "target/real-test-results/split";
    private static final String SAMPLE_FILE = "소프트웨어 설계의 정석.pdf";
    
    private File samplePdf;
    private File outputDir;
    private File splitOutputDir;
    
    @BeforeAll
    void setUp() throws IOException {
        // 테스트 PDF 파일 경로 설정
        samplePdf = new File(BOOKS_DIR, SAMPLE_FILE);
        
        // 실제 출력 디렉토리 설정 및 생성
        outputDir = new File(REAL_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        splitOutputDir = new File(REAL_SPLIT_OUTPUT_DIR);
        if (!splitOutputDir.exists()) {
            splitOutputDir.mkdirs();
        }
        
        // 테스트 파일 존재 확인
        assertThat(samplePdf)
                .withFailMessage("테스트 PDF 파일이 존재하지 않습니다: %s", samplePdf.getAbsolutePath())
                .exists();
                
        System.out.println("=== 실제 데이터 테스트 환경 설정 ===");
        System.out.println("테스트 파일: " + samplePdf.getAbsolutePath());
        System.out.println("출력 디렉토리: " + outputDir.getAbsolutePath());
        System.out.println("분할 출력 디렉토리: " + splitOutputDir.getAbsolutePath());
    }
    
    @Test
    @DisplayName("실제 PDF 파일 정보 분석 및 요약")
    void testRealPdfFileInfoAndSummary() throws IOException {
        // PDF 메타데이터 추출
        Map<String, String> metadata = PdfFileManager.extractMetadata(samplePdf);
        
        // PDF 품질 분석
        PdfFileManager.PdfQualityInfo qualityInfo = PdfFileManager.analyzeQuality(samplePdf);
        
        // PDF 요약 정보
        String summary = PdfFileManager.getSummary(samplePdf);
        
        // 결과 출력
        System.out.println("=== 실제 PDF 파일 분석 결과 ===");
        System.out.println("파일: " + samplePdf.getName());
        System.out.println("크기: " + PdfFileManager.formatFileSize(samplePdf.length()));
        System.out.println("페이지 수: " + metadata.get("PageCount"));
        System.out.println("품질 수준: " + qualityInfo.getQualityLevel());
        System.out.println("예상 DPI: " + qualityInfo.getEstimatedDpi());
        System.out.println("\n요약 정보:");
        System.out.println(summary);
        
        // 결과 검증
        assertThat(metadata)
                .withFailMessage("메타데이터가 비어있지 않아야 합니다")
                .isNotEmpty();
                
        assertThat(qualityInfo.getPageCount())
                .withFailMessage("페이지 수는 0보다 커야 합니다")
                .isGreaterThan(0);
                
        assertThat(summary)
                .withFailMessage("요약 정보가 비어있지 않아야 합니다")
                .isNotEmpty();
    }
    
    @Test
    @DisplayName("실제 PDF 파일에 워터마크 추가")
    void testAddWatermarkToRealPdf() throws IOException {
        // 출력 파일 설정
        File outputFile = new File(outputDir, "watermarked_" + samplePdf.getName());
        
        // 워터마크 추가
        long startTime = System.currentTimeMillis();
        File result = PdfFileManager.addTextWatermark(samplePdf, outputFile, "CONFIDENTIAL", 0.3f);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== 실제 PDF 워터마크 추가 결과 ===");
        System.out.println("입력 파일: " + samplePdf.getAbsolutePath());
        System.out.println("출력 파일: " + result.getAbsolutePath());
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        System.out.println("파일 크기 변화: " + PdfFileManager.formatFileSize(samplePdf.length()) + 
                " -> " + PdfFileManager.formatFileSize(result.length()));
        
        // 결과 검증
        assertThat(result)
                .withFailMessage("출력 파일이 생성되어야 합니다")
                .exists();
    }
    
    @Test
    @DisplayName("실제 PDF 파일을 200MB 이하로 분할")
    void testSplitRealPdfBySize() throws IOException {
        // 최대 파일 크기 설정 (200MB)
        int maxSizeMB = 180;
        
        // 파일 분할 실행
        long startTime = System.currentTimeMillis();
        File[] splitFiles = PdfFileManager.splitPdfBySize(samplePdf, splitOutputDir, maxSizeMB);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== 실제 PDF 파일 분할 결과 ===");
        System.out.println("입력 파일: " + samplePdf.getAbsolutePath());
        System.out.println("출력 디렉토리: " + splitOutputDir.getAbsolutePath());
        System.out.println("분할된 파일 수: " + splitFiles.length);
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        
        // 분할된 각 파일 정보 출력
        long totalSize = 0;
        for (File file : splitFiles) {
            long fileSize = file.length();
            totalSize += fileSize;
            
            // 각 파일의 페이지 수 확인
            int pageCount = getPageCount(file);
            
            System.out.println("- " + file.getName() + ": " + 
                    PdfFileManager.formatFileSize(fileSize) + ", " + 
                    pageCount + "페이지");
            
            // 파일 크기가 제한보다 작은지 검증
            assertThat(fileSize)
                    .withFailMessage("분할된 파일 %s의 크기가 %dMB를 초과합니다", 
                            file.getName(), maxSizeMB)
                    .isLessThanOrEqualTo(maxSizeMB * 1024L * 1024L);
        }
        
        System.out.println("총 크기: " + PdfFileManager.formatFileSize(totalSize));
        System.out.println("원본 크기: " + PdfFileManager.formatFileSize(samplePdf.length()));
        
        // 분할 결과 검증
        assertThat(splitFiles)
                .withFailMessage("분할된 파일이 하나 이상 생성되어야 합니다")
                .isNotEmpty();
    }
    
    @Test
    @DisplayName("books 디렉토리 내 모든 실제 PDF 파일 처리")
    void testProcessAllRealPdfFiles() throws IOException {
        // books 디렉토리 내 모든 PDF 파일 가져오기
        File booksDir = new File(BOOKS_DIR);
        File[] pdfFiles = booksDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("처리할 PDF 파일이 없습니다: " + booksDir.getAbsolutePath());
            return;
        }
        
        System.out.println("=== books 디렉토리 내 모든 실제 PDF 파일 처리 ===");
        System.out.println("파일 수: " + pdfFiles.length);
        
        // 각 파일별 처리
        for (File pdfFile : pdfFiles) {
            processRealPdfFile(pdfFile);
        }
        
        // 결과 검증
        File[] processedFiles = outputDir.listFiles((dir, name) -> 
                name.startsWith("processed_") && name.toLowerCase().endsWith(".pdf"));
        
        assertThat(processedFiles)
                .withFailMessage("처리된 파일이 하나 이상 생성되어야 합니다")
                .isNotNull()
                .isNotEmpty();
                
        System.out.println("처리된 파일 수: " + (processedFiles != null ? processedFiles.length : 0));
    }
    
    @Test
    @DisplayName("books 디렉토리 내 모든 PDF 파일을 200MB 이하로 분할")
    void testSplitAllRealPdfsBySize() throws IOException {
        // 최대 파일 크기 (200MB)
        int maxSizeMB = 200;
        
        // books 디렉토리 내 모든 PDF 파일 분할
        long startTime = System.currentTimeMillis();
        int totalProcessedFiles = PdfFileManager.splitAllPdfsBySize(
                new File(BOOKS_DIR), splitOutputDir, maxSizeMB);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("=== 모든 실제 PDF 파일 분할 결과 ===");
        System.out.println("입력 디렉토리: " + BOOKS_DIR);
        System.out.println("출력 디렉토리: " + splitOutputDir.getAbsolutePath());
        System.out.println("처리된 파일 수: " + totalProcessedFiles);
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        
        // 결과 디렉토리 확인
        File[] splitDirs = splitOutputDir.listFiles(File::isDirectory);
        if (splitDirs != null) {
            System.out.println("생성된 분할 디렉토리 수: " + splitDirs.length);
            
            // 파일 크기 제한 검증
            verifyFileSizeLimit(splitDirs, maxSizeMB);
        }
        
        // 결과 검증
        assertThat(totalProcessedFiles)
                .withFailMessage("하나 이상의 파일이 처리되어야 합니다")
                .isGreaterThan(0);
    }
    
    @Test
    @DisplayName("실제 PDF 파일 미리보기 이미지 생성")
    void testGeneratePreviewImagesForRealPdfs() throws IOException {
        // books 디렉토리 내 PDF 파일들
        File booksDir = new File(BOOKS_DIR);
        File[] pdfFiles = booksDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("처리할 PDF 파일이 없습니다.");
            return;
        }
        
        // 미리보기 이미지 저장 디렉토리
        File previewDir = new File(outputDir, "previews");
        if (!previewDir.exists()) {
            previewDir.mkdirs();
        }
        
        System.out.println("=== 실제 PDF 파일 미리보기 이미지 생성 ===");
        System.out.println("대상 파일 수: " + pdfFiles.length);
        System.out.println("출력 디렉토리: " + previewDir.getAbsolutePath());
        
        // 다양한 DPI 설정으로 미리보기 이미지 생성
        int[] dpiValues = {72, 150, 300};
        
        for (File pdfFile : pdfFiles) {
            System.out.println("\n파일: " + pdfFile.getName());
            
            for (int dpi : dpiValues) {
                // 해당 DPI용 디렉토리 생성
                File dpiDir = new File(previewDir, "dpi_" + dpi);
                if (!dpiDir.exists()) {
                    dpiDir.mkdirs();
                }
                
                // 미리보기 이미지 생성
                long startTime = System.currentTimeMillis();
                File previewImage = PdfFileManager.extractPreviewImage(pdfFile, dpiDir, dpi);
                long endTime = System.currentTimeMillis();
                
                System.out.println("- DPI " + dpi + ": " + 
                        previewImage.getName() + ", " + 
                        PdfFileManager.formatFileSize(previewImage.length()) + ", " + 
                        (endTime - startTime) + "ms");
                
                // 결과 검증
                assertThat(previewImage)
                        .withFailMessage("미리보기 이미지가 생성되어야 합니다")
                        .exists();
            }
        }
        
        // 결과 이미지 파일 확인
        int totalImageCount = countImageFiles(previewDir);
        System.out.println("\n총 생성된 미리보기 이미지 수: " + totalImageCount);
        
        assertThat(totalImageCount)
                .withFailMessage("미리보기 이미지가 생성되어야 합니다")
                .isGreaterThan(0);
    }
    
    @Test
    @DisplayName("실제 PDF 파일 병합 및 책갈피 추가")
    void testMergeRealPdfsWithBookmarks() throws IOException {
        // books 디렉토리 내 PDF 파일들
        File booksDir = new File(BOOKS_DIR);
        File[] pdfFiles = booksDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0 || pdfFiles.length < 2) {
            System.out.println("병합할 PDF 파일이 충분하지 않습니다. 최소 2개 이상 필요합니다.");
            return;
        }
        
        // 병합 파일 제한 (너무 많으면 메모리 문제 발생 가능)
        int maxFilesToMerge = Math.min(pdfFiles.length, 3);
        File[] filesToMerge = Arrays.copyOf(pdfFiles, maxFilesToMerge);
        
        // 책갈피 이름 설정
        String[] bookmarks = new String[maxFilesToMerge];
        for (int i = 0; i < maxFilesToMerge; i++) {
            String fileName = filesToMerge[i].getName();
            if (fileName.toLowerCase().endsWith(".pdf")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            bookmarks[i] = "책갈피 " + (i + 1) + ": " + fileName;
        }
        
        // 병합 파일 생성
        File mergedFile = new File(outputDir, "merged_with_bookmarks.pdf");
        
        System.out.println("=== 실제 PDF 파일 병합 및 책갈피 추가 ===");
        System.out.println("병합할 파일 수: " + maxFilesToMerge);
        
        for (int i = 0; i < maxFilesToMerge; i++) {
            System.out.println("- " + filesToMerge[i].getName() + " (" + bookmarks[i] + ")");
        }
        
        // 병합 실행
        long startTime = System.currentTimeMillis();
        File result = PdfFileManager.mergeWithBookmarks(mergedFile, bookmarks, filesToMerge);
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        long processingTime = endTime - startTime;
        System.out.println("\n병합 결과: " + result.getAbsolutePath());
        System.out.println("파일 크기: " + PdfFileManager.formatFileSize(result.length()));
        System.out.println("처리 시간: " + (processingTime / 1000.0) + "초");
        
        // 페이지 수 확인
        int pageCount = getPageCount(result);
        System.out.println("총 페이지 수: " + pageCount);
        
        // 결과 검증
        assertThat(result)
                .withFailMessage("병합된 파일이 생성되어야 합니다")
                .exists();
                
        assertThat(result.length())
                .withFailMessage("병합된 파일 크기는 0보다 커야 합니다")
                .isGreaterThan(0);
    }
    
    /**
     * 실제 PDF 파일 처리 헬퍼 메서드
     */
    private void processRealPdfFile(File pdfFile) throws IOException {
        System.out.println("\n처리 중: " + pdfFile.getName());
        
        // 요약 정보 출력
        String summary = PdfFileManager.getSummary(pdfFile);
        System.out.println(summary);
        
        // 파일 처리 (간단한 복사)
        File outputFile = new File(outputDir, "processed_" + pdfFile.getName());
        Files.copy(pdfFile.toPath(), outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("처리 완료: " + outputFile.getAbsolutePath());
        System.out.println("파일 크기: " + PdfFileManager.formatFileSize(outputFile.length()));
    }
    
    /**
     * 디렉토리 내 모든 분할 파일이 최대 크기 제한을 준수하는지 검증
     */
    private void verifyFileSizeLimit(File[] splitDirs, int maxSizeMB) {
        long maxSizeBytes = maxSizeMB * 1024L * 1024L;
        
        for (File dir : splitDirs) {
            File[] files = dir.listFiles(file -> file.getName().toLowerCase().endsWith(".pdf"));
            if (files == null || files.length == 0) continue;
            
            System.out.println("\n디렉토리: " + dir.getName() + " (" + files.length + "개 파일)");
            
            for (File file : files) {
                long fileSize = file.length();
                System.out.println("- " + file.getName() + ": " + PdfFileManager.formatFileSize(fileSize));
                
                // 파일 크기 검증
                assertThat(fileSize)
                        .withFailMessage("파일 %s의 크기가 %dMB를 초과합니다", 
                                file.getName(), maxSizeMB)
                        .isLessThanOrEqualTo(maxSizeBytes);
            }
        }
    }
    
    /**
     * PDF 파일의 페이지 수 확인
     */
    private int getPageCount(File pdfFile) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            return doc.getNumberOfPages();
        }
    }
    
    /**
     * 디렉토리 내 이미지 파일 수 계산
     */
    private int countImageFiles(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            return (int) paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || 
                               name.endsWith(".jpeg") || name.endsWith(".gif");
                    })
                    .count();
        }
    }
} 