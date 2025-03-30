package com.example.pdf.compress;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfPageSplitCompressor 클래스에 대한 테스트
 */
public class PdfPageSplitCompressorTest {

    @TempDir
    Path tempDir;

    private File samplePdf;
    private File multiPagePdf;
    private File outputDir;

    /**
     * 각 테스트 전에 실행되는 설정 메서드
     * 테스트에 필요한 샘플 PDF 파일을 생성합니다.
     */
    @BeforeEach
    void setUp() throws IOException {
        // 테스트용 출력 디렉토리 설정
        outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdir();

        // 단일 페이지 샘플 PDF 생성
        samplePdf = tempDir.resolve("sample.pdf").toFile();
        createSamplePdf(samplePdf, 1);

        // 여러 페이지 샘플 PDF 생성
        multiPagePdf = tempDir.resolve("multipage.pdf").toFile();
        createSamplePdf(multiPagePdf, 5);
    }

    /**
     * 각 테스트 후에 실행되는 정리 메서드
     */
    @AfterEach
    void tearDown() {
        // 필요한 경우 추가 정리 작업 수행
    }

    /**
     * 테스트용 샘플 PDF 파일을 생성하는 헬퍼 메서드
     *
     * @param file       생성할 PDF 파일
     * @param pageCount  생성할 페이지 수
     * @throws IOException 파일 생성 오류 시
     */
    private void createSamplePdf(File file, int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    // 텍스트 추가
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(100, 700);
                    contentStream.showText("Test Page " + (i + 1));
                    contentStream.endText();

                    // 간단한 도형 추가
                    contentStream.setStrokingColor(Color.RED);
                    contentStream.setLineWidth(2);
                    contentStream.addRect(100, 650, 100, 100);
                    contentStream.stroke();
                }
            }
            document.save(file);
        }
    }

    /**
     * PDF 파일의 페이지 수를 반환하는 헬퍼 메서드
     *
     * @param file PDF 파일
     * @return 페이지 수
     * @throws IOException 파일 읽기 오류 시
     */
    private int getPageCount(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * 페이지별 분리 압축 테스트
     */
    @Test
    void testCompressPdf() throws IOException {
        // 테스트 설정
        File outputFile = tempDir.resolve("compressed.pdf").toFile();
        
        // 메서드 실행
        long compressedSize = PdfPageSplitCompressor.compressPdf(
                multiPagePdf, outputFile);
        
        // 결과 검증
        assertThat(outputFile)
                .withFailMessage("압축된 파일이 존재해야 합니다")
                .exists();
                
        assertThat(getPageCount(outputFile))
                .withFailMessage("원본 파일과 압축된 파일의 페이지 수가 같아야 합니다")
                .isEqualTo(getPageCount(multiPagePdf));
                
        assertThat(compressedSize)
                .withFailMessage("압축된 파일 크기가 0보다 커야 합니다")
                .isGreaterThan(0);
    }

    /**
     * 병렬 압축 테스트
     */
    @Test
    void testCompressPdfParallel() throws IOException {
        // 테스트 설정
        File outputFile = tempDir.resolve("compressed_parallel.pdf").toFile();
        
        // 메서드 실행
        long compressedSize = PdfPageSplitCompressor.compressPdfParallel(
                multiPagePdf, outputFile);
        
        // 결과 검증
        assertThat(outputFile)
                .withFailMessage("압축된 파일이 존재해야 합니다")
                .exists();
                
        assertThat(getPageCount(outputFile))
                .withFailMessage("원본 파일과 압축된 파일의 페이지 수가 같아야 합니다")
                .isEqualTo(getPageCount(multiPagePdf));
                
        assertThat(compressedSize)
                .withFailMessage("압축된 파일 크기가 0보다 커야 합니다")
                .isGreaterThan(0);
    }

    /**
     * 접미사가 붙은 압축 파일 생성 테스트
     */
    @Test
    void testCompressPdfWithPostfix() throws IOException {
        // 메서드 실행
        long compressedSize = PdfPageSplitCompressor.compressPdfWithPostfix(multiPagePdf);
        
        // 결과 검증
        String baseName = multiPagePdf.getName().substring(0, multiPagePdf.getName().lastIndexOf("."));
        String ext = multiPagePdf.getName().substring(multiPagePdf.getName().lastIndexOf("."));
        File expectedFile = new File(multiPagePdf.getParent(), baseName + "_압축_페이지분리" + ext);
        
        assertThat(expectedFile)
                .withFailMessage("접미사가 붙은 압축 파일이 존재해야 합니다")
                .exists();
                
        assertThat(getPageCount(expectedFile))
                .withFailMessage("원본 파일과 압축된 파일의 페이지 수가 같아야 합니다")
                .isEqualTo(getPageCount(multiPagePdf));
                
        assertThat(compressedSize)
                .withFailMessage("압축된 파일 크기가 0보다 커야 합니다")
                .isGreaterThan(0);
        
        // 테스트 후 파일 정리
        expectedFile.delete();
    }

    /**
     * PDF 페이지 분리 테스트
     */
    @Test
    void testSplitPdfToPages() throws IOException {
        // 메서드 실행
        int pageCount = PdfPageSplitCompressor.splitPdfToPages(multiPagePdf, outputDir);
        
        // 결과 검증
        assertThat(pageCount)
                .withFailMessage("분리된 페이지 수가 원본 PDF의 페이지 수와 같아야 합니다")
                .isEqualTo(getPageCount(multiPagePdf));
        
        File[] splitFiles = outputDir.listFiles((dir, name) -> name.startsWith("page_") && name.endsWith(".pdf"));
        assertThat(splitFiles)
                .withFailMessage("분리된 파일이 존재해야 합니다")
                .isNotNull();
                
        assertThat(splitFiles)
                .withFailMessage("분리된 파일 수가 페이지 수와 일치해야 합니다")
                .hasSize(pageCount);
        
        // 각 파일이 단일 페이지인지 확인
        for (File file : splitFiles) {
            assertThat(getPageCount(file))
                    .withFailMessage("분리된 각 파일은 단일 페이지여야 합니다: %s", file.getName())
                    .isEqualTo(1);
        }
    }

    /**
     * 기본 디렉토리에 PDF 페이지 분리 테스트
     */
    @Test
    void testSplitPdfToPagesWithDefaultDir() throws IOException {
        // 메서드 실행
        int pageCount = PdfPageSplitCompressor.splitPdfToPagesWithDefaultDir(multiPagePdf);
        
        // 결과 검증
        String baseName = multiPagePdf.getName().substring(0, multiPagePdf.getName().lastIndexOf("."));
        File expectedDir = new File(multiPagePdf.getParent(), baseName + "_페이지분리");
        
        assertThat(expectedDir)
                .withFailMessage("생성된 디렉토리가 존재해야 합니다")
                .exists();
                
        assertThat(expectedDir)
                .withFailMessage("생성된 경로가 디렉토리여야 합니다")
                .isDirectory();
        
        File[] splitFiles = expectedDir.listFiles((dir, name) -> name.startsWith("page_") && name.endsWith(".pdf"));
        assertThat(splitFiles)
                .withFailMessage("분리된 파일이 존재해야 합니다")
                .isNotNull();
                
        assertThat(splitFiles)
                .withFailMessage("분리된 파일 수가 페이지 수와 일치해야 합니다")
                .hasSize(pageCount);
        
        // 테스트 후 디렉토리와 파일 정리
        Arrays.stream(Objects.requireNonNull(expectedDir.listFiles())).forEach(File::delete);
        expectedDir.delete();
    }

    /**
     * 페이지 범위 추출 테스트
     */
    @Test
    void testExtractPageRange() throws IOException {
        // 테스트 설정
        File outputFile = tempDir.resolve("extracted_range.pdf").toFile();
        int startPage = 2;
        int endPage = 4;
        
        // 메서드 실행
        int extractedCount = PdfPageSplitCompressor.extractPageRange(
                multiPagePdf, outputFile, startPage, endPage);
        
        // 결과 검증
        assertThat(extractedCount)
                .withFailMessage("추출된 페이지 수가 범위와 일치해야 합니다")
                .isEqualTo(endPage - startPage + 1);
                
        assertThat(getPageCount(outputFile))
                .withFailMessage("출력 파일의 페이지 수가 추출된 페이지 수와 일치해야 합니다")
                .isEqualTo(extractedCount);
    }

    /**
     * 특정 페이지 추출 테스트
     */
    @Test
    void testExtractSpecificPages() throws IOException {
        // 테스트 설정
        File outputFile = tempDir.resolve("extracted_specific.pdf").toFile();
        int[] pageNumbers = {1, 3, 5}; // 1, 3, 5 페이지 선택
        
        // 메서드 실행
        int extractedCount = PdfPageSplitCompressor.extractSpecificPages(
                multiPagePdf, outputFile, pageNumbers);
        
        // 결과 검증
        assertThat(extractedCount)
                .withFailMessage("추출된 페이지 수가 요청한 페이지 수와 일치해야 합니다")
                .isEqualTo(pageNumbers.length);
                
        assertThat(getPageCount(outputFile))
                .withFailMessage("출력 파일의 페이지 수가 추출된 페이지 수와 일치해야 합니다")
                .isEqualTo(extractedCount);
    }

    /**
     * PDF 파일 병합 테스트
     */
    @Test
    void testMergePdfFiles() throws IOException {
        // 테스트 설정
        File outputFile = tempDir.resolve("merged.pdf").toFile();
        
        // 추가 테스트 파일 생성
        File additionalPdf = tempDir.resolve("additional.pdf").toFile();
        createSamplePdf(additionalPdf, 3);
        
        // 메서드 실행
        int mergedPageCount = PdfPageSplitCompressor.mergePdfFiles(
                outputFile, samplePdf, multiPagePdf, additionalPdf);
        
        // 결과 검증
        int expectedPageCount = getPageCount(samplePdf) + getPageCount(multiPagePdf) + getPageCount(additionalPdf);
        assertThat(mergedPageCount)
                .withFailMessage("병합된 총 페이지 수가 입력 파일들의 페이지 수 합과 일치해야 합니다")
                .isEqualTo(expectedPageCount);
                
        assertThat(getPageCount(outputFile))
                .withFailMessage("출력 파일의 페이지 수가 병합된 페이지 수와 일치해야 합니다")
                .isEqualTo(mergedPageCount);
    }
    
    /**
     * 유효하지 않은 페이지 범위에 대한 예외 테스트
     */
    @Test
    void testInvalidPageRange() {
        // 테스트 설정
        File outputFile = tempDir.resolve("invalid_range.pdf").toFile();
        
        // 유효하지 않은 시작 페이지
        assertThatThrownBy(() -> PdfPageSplitCompressor.extractPageRange(multiPagePdf, outputFile, 0, 3))
                .withFailMessage("시작 페이지가 0인 경우 예외가 발생해야 합니다")
                .isInstanceOf(IllegalArgumentException.class);
        
        // 범위가 전체 페이지 수를 초과
        assertThatThrownBy(() -> PdfPageSplitCompressor.extractPageRange(multiPagePdf, outputFile, 6, 10))
                .withFailMessage("범위가 전체 페이지 수를 초과하는 경우 예외가 발생해야 합니다")
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    /**
     * 빈 입력 파일 배열에 대한 예외 테스트
     */
    @Test
    void testEmptyInputFilesForMerge() {
        // 테스트 설정
        File outputFile = tempDir.resolve("empty_merge.pdf").toFile();
        
        // 빈 입력 파일 배열
        assertThatThrownBy(() -> PdfPageSplitCompressor.mergePdfFiles(outputFile))
                .withFailMessage("빈 입력 파일 배열에 대해 예외가 발생해야 합니다")
                .isInstanceOf(IllegalArgumentException.class);
    }
} 