package com.example.pdf.compress;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PDF 파일의 페이지별 분리 압축을 수행하는 유틸리티 클래스.
 * 대용량 PDF 파일을 처리할 때 효과적이며, 메모리 사용을 최적화합니다.
 */
public class PdfPageSplitCompressor {
    private static final Logger log = LoggerFactory.getLogger(PdfPageSplitCompressor.class);
    private static final String COMPRESSION_POSTFIX = "_압축_페이지분리";
    private static final String PARALLEL_COMPRESSION_POSTFIX = "_압축_병렬분리";
    private static final String SPLIT_POSTFIX = "_페이지분리";
    private static final long MIN_SIZE_FOR_COMPRESSION = 1024;
    
    /**
     * 페이지별 분리 압축 방식으로 PDF 파일을 압축합니다.
     *
     * @param inputFile  입력 PDF 파일
     * @param outputFile 출력 PDF 파일
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdf(File inputFile, File outputFile) throws IOException {
        return compressPdf(inputFile, outputFile, PdfCompressionUtil.CompressionProfile.VERY_LIGHT_COMPRESSION);
    }
    
    /**
     * 페이지별 분리 압축 방식으로 PDF 파일을 압축합니다.
     *
     * @param inputFile  입력 PDF 파일
     * @param outputFile 출력 PDF 파일
     * @param profile    사용할 압축 프로필
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdf(File inputFile, File outputFile, PdfCompressionUtil.CompressionProfile profile) throws IOException {
        log.info("페이지별 분리 압축 시작: {}, 원본 크기: {} 바이트, 프로필: {}", 
            inputFile.getName(), inputFile.length(), profile.name());
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
        
        // 임시 디렉토리 생성
        Path tempDir = Files.createTempDirectory("pdf_compress_pages_");
        Path compressedTempDir = Files.createTempDirectory("pdf_compress_pages_result_");
        AtomicInteger successCount = new AtomicInteger(0);
        
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            int pageCount = document.getNumberOfPages();
            log.info("PDF 문서 로드 완료, 총 {} 페이지 분리 압축 시작", pageCount);
            
            // 1. 각 페이지를 개별 PDF로 분리
            for (int i = 0; i < pageCount; i++) {
                try (PDDocument singlePageDoc = new PDDocument()) {
                    PDPage page = document.getPage(i);
                    singlePageDoc.addPage(page);
                    
                    File pageFile = tempDir.resolve("page_" + (i + 1) + ".pdf").toFile();
                    singlePageDoc.save(pageFile);
                    log.debug("페이지 {} 추출 완료: {}", i + 1, pageFile.getAbsolutePath());
                }
            }
            
            // 원본 문서 닫기 (메모리 해제)
            document.close();
            System.gc();
            
            // 2. 각 페이지 개별 압축
            File[] pageFiles = tempDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (pageFiles != null) {
                log.info("총 {} 개별 페이지 압축 시작", pageFiles.length);
                
                for (File pageFile : pageFiles) {
                    try {
                        File compressedPage = compressedTempDir.resolve(pageFile.getName()).toFile();
                        // 개별 페이지 압축 실행
                        long compressedSize = PdfCompressionUtil.compressPdf(pageFile, compressedPage, profile);
                        
                        if (compressedSize > 0) {
                            successCount.incrementAndGet();
                            log.debug("페이지 {} 압축 성공: {} -> {} 바이트", 
                                pageFile.getName(), pageFile.length(), compressedSize);
                        }
                    } catch (Exception e) {
                        log.error("페이지 {} 압축 중 오류 발생: {}", pageFile.getName(), e.getMessage());
                    }
                }
            }
            
            // 3. 압축된 페이지들을 다시 하나의 PDF로 병합
            try (PDDocument mergedDoc = new PDDocument()) {
                File[] compressedPageFiles = compressedTempDir.toFile().listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".pdf"));
                
                if (compressedPageFiles != null) {
                    // 페이지 번호 순서대로 정렬
                    java.util.Arrays.sort(compressedPageFiles, Comparator.comparing(f -> {
                        String name = f.getName();
                        try {
                            // "page_숫자.pdf" 형식에서 숫자 부분 추출
                            return Integer.parseInt(name.substring(5, name.length() - 4));
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }));
                    
                    for (File pageFile : compressedPageFiles) {
                        try (PDDocument pageDoc = Loader.loadPDF(pageFile)) {
                            for (int i = 0; i < pageDoc.getNumberOfPages(); i++) {
                                PDPage page = pageDoc.getPage(i);
                                mergedDoc.addPage(page);
                            }
                        } catch (Exception e) {
                            log.warn("페이지 {} 병합 중 오류 발생: {}", pageFile.getName(), e.getMessage());
                        }
                    }
                    
                    // 병합된 PDF 저장
                    log.info("압축된 {} 페이지 병합하여 저장 중...", mergedDoc.getNumberOfPages());
                    mergedDoc.save(outputFile);
                } else {
                    log.error("압축된 페이지 파일을 찾을 수 없습니다.");
                    Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return originalSize;
                }
            }
            
            long compressedSize = outputFile.length();
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 압축 통계 계산
            double compressionRatio = originalSize > 0 ? (1 - (double) compressedSize / originalSize) * 100 : 0;
            
            // 압축이 실패한 경우 원본 파일을 복사
            if (compressedSize >= originalSize) {
                log.warn("페이지별 압축이 효과적이지 않습니다. 원본 파일을 유지합니다. (원본: {} bytes, 압축: {} bytes)",
                    originalSize, compressedSize);
                
                if (!inputFile.equals(outputFile)) {
                    Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return originalSize;
            }
            
            // 성공적인 압축 결과 로깅
            log.info("페이지별 압축 완료: 처리 시간: {}ms, 성공한 페이지: {}/{}", 
                processingTime, successCount.get(), pageCount);
            log.info("압축 결과: 원본: {} bytes → 압축: {} bytes ({}% 감소)",
                originalSize, compressedSize, String.format("%.2f", compressionRatio));
            
            return compressedSize;
            
        } finally {
            // 임시 파일 정리
            try {
                deleteDirectory(tempDir.toFile());
                deleteDirectory(compressedTempDir.toFile());
                log.debug("임시 파일 정리 완료");
            } catch (Exception e) {
                log.warn("임시 파일 정리 중 오류 발생: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 디렉토리와 그 내용을 재귀적으로 삭제합니다.
     *
     * @param directory 삭제할 디렉토리
     */
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 페이지별 분리 압축 방식으로 PDF 파일을 압축하고 "_압축_페이지분리" 접미사가 붙은 파일을 생성합니다.
     *
     * @param inputFile 입력 PDF 파일
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfWithPostfix(File inputFile) throws IOException {
        return compressPdfWithPostfix(inputFile, PdfCompressionUtil.CompressionProfile.VERY_LIGHT_COMPRESSION);
    }
    
    /**
     * 페이지별 분리 압축 방식으로 PDF 파일을 압축하고 "_압축_페이지분리" 접미사가 붙은 파일을 생성합니다.
     *
     * @param inputFile 입력 PDF 파일
     * @param profile   사용할 압축 프로필
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfWithPostfix(File inputFile, PdfCompressionUtil.CompressionProfile profile) throws IOException {
        // 파일 이름과 확장자 분리
        String fileName = inputFile.getName();
        String fileExtension = "";
        String baseName = fileName;
        
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            fileExtension = fileName.substring(lastDotIndex); // 확장자 (.pdf 포함)
            baseName = fileName.substring(0, lastDotIndex);   // 확장자 제외한 파일명
        }
        
        // 새 파일 이름 생성 (예: sample.pdf -> sample_압축_페이지분리.pdf)
        String newFileName = baseName + COMPRESSION_POSTFIX + fileExtension;
        
        // 원본 파일과 같은 디렉토리에 새 파일 생성
        File outputFile = new File(inputFile.getParent(), newFileName);
        
        log.info("페이지별 분리 압축 결과 파일 생성 (프로필: {}): {}", 
            profile.name(), outputFile.getAbsolutePath());
        
        // 페이지별 분리 압축 실행
        return compressPdf(inputFile, outputFile, profile);
    }
    
    /**
     * 고용량 PDF 파일을 위한 병렬 페이지 압축 방식으로 PDF 파일을 압축합니다.
     * 각 페이지를 멀티스레드로 병렬 처리하여 압축 속도를 향상시킵니다.
     *
     * @param inputFile  입력 PDF 파일
     * @param outputFile 출력 PDF 파일
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfParallel(File inputFile, File outputFile) throws IOException {
        return compressPdfParallel(inputFile, outputFile, PdfCompressionUtil.CompressionProfile.VERY_LIGHT_COMPRESSION);
    }
    
    /**
     * 고용량 PDF 파일을 위한 병렬 페이지 압축 방식으로 PDF 파일을 압축합니다.
     * 각 페이지를 멀티스레드로 병렬 처리하여 압축 속도를 향상시킵니다.
     *
     * @param inputFile  입력 PDF 파일
     * @param outputFile 출력 PDF 파일
     * @param profile    사용할 압축 프로필
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfParallel(File inputFile, File outputFile, PdfCompressionUtil.CompressionProfile profile) throws IOException {
        log.info("병렬 페이지 압축 시작: {}, 원본 크기: {} 바이트, 프로필: {}", 
            inputFile.getName(), inputFile.length(), profile.name());
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
        
        // 임시 디렉토리 생성
        Path tempDir = Files.createTempDirectory("pdf_compress_parallel_");
        Path compressedTempDir = Files.createTempDirectory("pdf_compress_parallel_result_");
        AtomicInteger successCount = new AtomicInteger(0);
        
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            int pageCount = document.getNumberOfPages();
            log.info("PDF 문서 로드 완료, 총 {} 페이지 병렬 압축 시작", pageCount);
            
            // 1. 각 페이지를 개별 PDF로 분리
            for (int i = 0; i < pageCount; i++) {
                try (PDDocument singlePageDoc = new PDDocument()) {
                    PDPage page = document.getPage(i);
                    singlePageDoc.addPage(page);
                    
                    File pageFile = tempDir.resolve("page_" + (i + 1) + ".pdf").toFile();
                    singlePageDoc.save(pageFile);
                    log.debug("페이지 {} 추출 완료: {}", i + 1, pageFile.getAbsolutePath());
                }
            }
            
            // 원본 문서 닫기 (메모리 해제)
            document.close();
            System.gc();
            
            // 2. 각 페이지 병렬 압축
            File[] pageFiles = tempDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (pageFiles != null) {
                log.info("총 {} 개별 페이지 병렬 압축 시작", pageFiles.length);
                
                // 병렬 스트림 사용하여 각 페이지 압축
                java.util.Arrays.stream(pageFiles).parallel().forEach(pageFile -> {
                    try {
                        File compressedPage = compressedTempDir.resolve(pageFile.getName()).toFile();
                        // 개별 페이지 압축 실행
                        long compressedSize = PdfCompressionUtil.compressPdf(pageFile, compressedPage, profile);
                        
                        if (compressedSize > 0) {
                            successCount.incrementAndGet();
                            log.debug("페이지 {} 압축 성공: {} -> {} 바이트", 
                                pageFile.getName(), pageFile.length(), compressedSize);
                        }
                    } catch (Exception e) {
                        log.error("페이지 {} 압축 중 오류 발생: {}", pageFile.getName(), e.getMessage());
                    }
                });
            }
            
            // 3. 압축된 페이지들을 다시 하나의 PDF로 병합
            try (PDDocument mergedDoc = new PDDocument()) {
                File[] compressedPageFiles = compressedTempDir.toFile().listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".pdf"));
                
                if (compressedPageFiles != null) {
                    // 페이지 번호 순서대로 정렬
                    java.util.Arrays.sort(compressedPageFiles, Comparator.comparing(f -> {
                        String name = f.getName();
                        try {
                            // "page_숫자.pdf" 형식에서 숫자 부분 추출
                            return Integer.parseInt(name.substring(5, name.length() - 4));
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }));
                    
                    for (File pageFile : compressedPageFiles) {
                        try (PDDocument pageDoc = Loader.loadPDF(pageFile)) {
                            for (int i = 0; i < pageDoc.getNumberOfPages(); i++) {
                                PDPage page = pageDoc.getPage(i);
                                mergedDoc.addPage(page);
                            }
                        } catch (Exception e) {
                            log.warn("페이지 {} 병합 중 오류 발생: {}", pageFile.getName(), e.getMessage());
                        }
                    }
                    
                    // 병합된 PDF 저장
                    log.info("압축된 {} 페이지 병합하여 저장 중...", mergedDoc.getNumberOfPages());
                    mergedDoc.save(outputFile);
                } else {
                    log.error("압축된 페이지 파일을 찾을 수 없습니다.");
                    Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return originalSize;
                }
            }
            
            long compressedSize = outputFile.length();
            long processingTime = System.currentTimeMillis() - startTime;
            
            // 압축 통계 계산
            double compressionRatio = originalSize > 0 ? (1 - (double) compressedSize / originalSize) * 100 : 0;
            
            // 압축이 실패한 경우 원본 파일을 복사
            if (compressedSize >= originalSize) {
                log.warn("병렬 페이지 압축이 효과적이지 않습니다. 원본 파일을 유지합니다. (원본: {} bytes, 압축: {} bytes)",
                    originalSize, compressedSize);
                
                if (!inputFile.equals(outputFile)) {
                    Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return originalSize;
            }
            
            // 성공적인 압축 결과 로깅
            log.info("병렬 페이지 압축 완료: 처리 시간: {}ms, 성공한 페이지: {}/{}", 
                processingTime, successCount.get(), pageCount);
            log.info("압축 결과: 원본: {} bytes → 압축: {} bytes ({}% 감소)",
                originalSize, compressedSize, String.format("%.2f", compressionRatio));
            
            return compressedSize;
            
        } finally {
            // 임시 파일 정리
            try {
                deleteDirectory(tempDir.toFile());
                deleteDirectory(compressedTempDir.toFile());
                log.debug("임시 파일 정리 완료");
            } catch (Exception e) {
                log.warn("임시 파일 정리 중 오류 발생: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 병렬 페이지 압축 방식으로 PDF 파일을 압축하고 "_압축_병렬분리" 접미사가 붙은 파일을 생성합니다.
     *
     * @param inputFile 입력 PDF 파일
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfParallelWithPostfix(File inputFile) throws IOException {
        return compressPdfParallelWithPostfix(inputFile, PdfCompressionUtil.CompressionProfile.VERY_LIGHT_COMPRESSION);
    }
    
    /**
     * 병렬 페이지 압축 방식으로 PDF 파일을 압축하고 "_압축_병렬분리" 접미사가 붙은 파일을 생성합니다.
     *
     * @param inputFile 입력 PDF 파일
     * @param profile   사용할 압축 프로필
     * @return 압축된 파일의 크기 (바이트)
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static long compressPdfParallelWithPostfix(File inputFile, PdfCompressionUtil.CompressionProfile profile) throws IOException {
        // 파일 이름과 확장자 분리
        String fileName = inputFile.getName();
        String fileExtension = "";
        String baseName = fileName;
        
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            fileExtension = fileName.substring(lastDotIndex); // 확장자 (.pdf 포함)
            baseName = fileName.substring(0, lastDotIndex);   // 확장자 제외한 파일명
        }
        
        // 새 파일 이름 생성 (예: sample.pdf -> sample_압축_병렬분리.pdf)
        String newFileName = baseName + PARALLEL_COMPRESSION_POSTFIX + fileExtension;
        
        // 원본 파일과 같은 디렉토리에 새 파일 생성
        File outputFile = new File(inputFile.getParent(), newFileName);
        
        log.info("병렬 페이지 압축 결과 파일 생성 (프로필: {}): {}", 
            profile.name(), outputFile.getAbsolutePath());
        
        // 병렬 페이지 압축 실행
        return compressPdfParallel(inputFile, outputFile, profile);
    }
    
    /**
     * PDF 파일을 압축 없이 페이지별로 분리합니다.
     * 각 페이지는 별도의 PDF 파일로 저장됩니다.
     *
     * @param inputFile   입력 PDF 파일
     * @param outputDir   출력 디렉토리 (없으면 자동 생성)
     * @param filePrefix  생성될 파일의 접두사 (예: "page_")
     * @return 생성된 파일 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int splitPdfToPages(File inputFile, File outputDir, String filePrefix) throws IOException {
        log.info("PDF 페이지 분리 시작: {}, 출력 디렉토리: {}", inputFile.getName(), outputDir.getAbsolutePath());
        long startTime = System.currentTimeMillis();
        
        // 출력 디렉토리가 없으면 생성
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("출력 디렉토리를 생성할 수 없습니다: " + outputDir.getAbsolutePath());
            }
        }
        
        AtomicInteger pageCount = new AtomicInteger(0);
        
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            int totalPages = document.getNumberOfPages();
            log.info("PDF 문서 로드 완료, 총 {} 페이지 분리 시작", totalPages);
            
            // 각 페이지를 개별 PDF로 분리
            for (int i = 0; i < totalPages; i++) {
                try (PDDocument singlePageDoc = new PDDocument()) {
                    PDPage page = document.getPage(i);
                    singlePageDoc.addPage(page);
                    
                    // 페이지 번호는 1부터 시작하도록 i+1 사용
                    String pageNumber = String.format("%03d", i + 1); // 001, 002, ... 형식으로 숫자 포맷팅
                    File pageFile = new File(outputDir, filePrefix + pageNumber + ".pdf");
                    
                    singlePageDoc.save(pageFile);
                    pageCount.incrementAndGet();
                    log.debug("페이지 {} 추출 완료: {}", i + 1, pageFile.getAbsolutePath());
                }
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("PDF 페이지 분리 완료: 처리 시간: {}ms, 분리된 페이지: {}/{}", 
                processingTime, pageCount.get(), totalPages);
            
            return pageCount.get();
        }
    }
    
    /**
     * PDF 파일을 압축 없이 페이지별로 분리합니다.
     * 각 페이지는 별도의 PDF 파일로 저장됩니다.
     * 파일명은 기본값으로 "page_001.pdf", "page_002.pdf" 등의 형식으로 생성됩니다.
     *
     * @param inputFile   입력 PDF 파일
     * @param outputDir   출력 디렉토리 (없으면 자동 생성)
     * @return 생성된 파일 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int splitPdfToPages(File inputFile, File outputDir) throws IOException {
        return splitPdfToPages(inputFile, outputDir, "page_");
    }
    
    /**
     * PDF 파일을 압축 없이 페이지별로 분리하고 원본 파일과 같은 디렉토리에 새 폴더를 생성하여 저장합니다.
     * 새 폴더 이름은 원본 파일 이름에 "_pages" 접미사가 붙습니다.
     *
     * @param inputFile  입력 PDF 파일
     * @return 생성된 파일 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int splitPdfToPagesWithDefaultDir(File inputFile) throws IOException {
        // 파일 이름과 확장자 분리
        String fileName = inputFile.getName();
        String baseName = fileName;
        
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            baseName = fileName.substring(0, lastDotIndex); // 확장자 제외한 파일명
        }
        
        // 새 디렉토리 이름 생성 (예: sample.pdf -> sample_pages)
        String dirName = baseName + SPLIT_POSTFIX;
        
        // 원본 파일과 같은 디렉토리에 새 폴더 생성
        File outputDir = new File(inputFile.getParent(), dirName);
        
        log.info("PDF 페이지 분리 폴더 생성: {}", outputDir.getAbsolutePath());
        
        // 페이지 분리 실행
        return splitPdfToPages(inputFile, outputDir);
    }
    
    /**
     * PDF 파일의 각 페이지를, 주어진 페이지 번호 범위만 포함하는 새 PDF 파일로 저장합니다.
     *
     * @param inputFile   입력 PDF 파일
     * @param outputFile  출력 PDF 파일
     * @param startPage   시작 페이지 번호 (1부터 시작)
     * @param endPage     종료 페이지 번호 (포함)
     * @return 추출된 페이지 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int extractPageRange(File inputFile, File outputFile, int startPage, int endPage) throws IOException {
        log.info("PDF 페이지 범위 추출 시작: {}, 페이지 범위: {}-{}", 
            inputFile.getName(), startPage, endPage);
        long startTime = System.currentTimeMillis();
        
        if (startPage < 1) {
            throw new IllegalArgumentException("시작 페이지는 1 이상이어야 합니다: " + startPage);
        }
        
        try (PDDocument sourceDoc = Loader.loadPDF(inputFile)) {
            int totalPages = sourceDoc.getNumberOfPages();
            
            // 페이지 인덱스 조정 (PDF는 0부터, 사용자 입력은 1부터 시작)
            int startIndex = startPage - 1;
            int endIndex = Math.min(endPage - 1, totalPages - 1);
            
            if (startIndex > endIndex || startIndex >= totalPages) {
                throw new IllegalArgumentException(
                    String.format("유효하지 않은 페이지 범위: %d-%d (총 페이지 수: %d)", 
                        startPage, endPage, totalPages));
            }
            
            try (PDDocument newDoc = new PDDocument()) {
                int extractedCount = 0;
                
                // 지정된 범위의 페이지만 추출
                for (int i = startIndex; i <= endIndex; i++) {
                    PDPage page = sourceDoc.getPage(i);
                    newDoc.addPage(page);
                    extractedCount++;
                }
                
                // 새 PDF 저장
                newDoc.save(outputFile);
                
                long processingTime = System.currentTimeMillis() - startTime;
                log.info("PDF 페이지 범위 추출 완료: 처리 시간: {}ms, 추출된 페이지: {}", 
                    processingTime, extractedCount);
                
                return extractedCount;
            }
        }
    }
    
    /**
     * PDF 파일의 각 페이지를, 주어진 페이지 번호 배열만 포함하는 새 PDF 파일로 저장합니다.
     *
     * @param inputFile    입력 PDF 파일
     * @param outputFile   출력 PDF 파일
     * @param pageNumbers  추출할 페이지 번호 배열 (1부터 시작)
     * @return 추출된 페이지 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int extractSpecificPages(File inputFile, File outputFile, int[] pageNumbers) throws IOException {
        log.info("PDF 특정 페이지 추출 시작: {}, 페이지 개수: {}", 
            inputFile.getName(), pageNumbers.length);
        long startTime = System.currentTimeMillis();
        
        try (PDDocument sourceDoc = Loader.loadPDF(inputFile)) {
            int totalPages = sourceDoc.getNumberOfPages();
            
            try (PDDocument newDoc = new PDDocument()) {
                int extractedCount = 0;
                
                // 지정된 페이지만 추출
                for (int pageNumber : pageNumbers) {
                    // 페이지 인덱스 조정 (PDF는 0부터, 사용자 입력은 1부터 시작)
                    int pageIndex = pageNumber - 1;
                    
                    if (pageIndex >= 0 && pageIndex < totalPages) {
                        PDPage page = sourceDoc.getPage(pageIndex);
                        newDoc.addPage(page);
                        extractedCount++;
                    } else {
                        log.warn("유효하지 않은 페이지 번호 무시: {} (총 페이지 수: {})", 
                            pageNumber, totalPages);
                    }
                }
                
                // 새 PDF 저장
                newDoc.save(outputFile);
                
                long processingTime = System.currentTimeMillis() - startTime;
                log.info("PDF 특정 페이지 추출 완료: 처리 시간: {}ms, 추출된 페이지: {}/{}", 
                    processingTime, extractedCount, pageNumbers.length);
                
                return extractedCount;
            }
        }
    }
    
    /**
     * 여러 PDF 파일을 하나로 병합합니다.
     *
     * @param outputFile  출력 PDF 파일
     * @param inputFiles  병합할 PDF 파일 배열
     * @return 병합된 페이지 수
     * @throws IOException 파일 처리 중 오류 발생 시
     */
    public static int mergePdfFiles(File outputFile, File... inputFiles) throws IOException {
        log.info("PDF 파일 병합 시작: {} 개 파일 -> {}", 
            inputFiles.length, outputFile.getName());
        long startTime = System.currentTimeMillis();
        
        if (inputFiles.length == 0) {
            throw new IllegalArgumentException("병합할 파일이 없습니다");
        }
        
        int totalPages = 0;
        
        try (PDDocument mergedDoc = new PDDocument()) {
            for (File inputFile : inputFiles) {
                try (PDDocument doc = Loader.loadPDF(inputFile)) {
                    int pageCount = doc.getNumberOfPages();
                    
                    for (int i = 0; i < pageCount; i++) {
                        // 페이지를 새 문서로 복사 (깊은 복사)
                        PDPage importedPage = mergedDoc.importPage(doc.getPage(i));
                        totalPages++;
                    }
                    
                    log.debug("파일 추가됨: {}, 페이지 수: {}", inputFile.getName(), pageCount);
                } catch (Exception e) {
                    log.error("파일 병합 중 오류 발생: {}, 오류: {}", inputFile.getName(), e.getMessage());
                }
            }
            
            // 병합된 PDF 저장
            mergedDoc.save(outputFile);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("PDF 파일 병합 완료: 처리 시간: {}ms, 총 페이지 수: {}", 
                processingTime, totalPages);
            
            return totalPages;
        }
    }
} 