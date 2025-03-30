package com.example.pdf.compress;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * PDF 파일 관리 및 처리를 위한 유틸리티 클래스
 */
public class PdfFileManager {

    /**
     * PDF 파일로부터 메타데이터를 추출
     * 
     * @param pdfFile PDF 파일
     * @return 메타데이터 맵
     * @throws IOException 파일 로드 오류 발생 시
     */
    public static Map<String, String> extractMetadata(File pdfFile) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        
        // 파일 기본 정보 추가
        metadata.put("FileName", pdfFile.getName());
        metadata.put("FileSize", formatFileSize(pdfFile.length()));
        metadata.put("LastModified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(pdfFile.lastModified())));
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            // 문서 정보 추가
            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                if (info.getTitle() != null) metadata.put("Title", info.getTitle());
                if (info.getAuthor() != null) metadata.put("Author", info.getAuthor());
                if (info.getSubject() != null) metadata.put("Subject", info.getSubject());
                if (info.getKeywords() != null) metadata.put("Keywords", info.getKeywords());
                if (info.getCreator() != null) metadata.put("Creator", info.getCreator());
                if (info.getProducer() != null) metadata.put("Producer", info.getProducer());
                if (info.getCreationDate() != null) {
                    metadata.put("CreationDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getCreationDate().getTime()));
                }
                if (info.getModificationDate() != null) {
                    metadata.put("ModificationDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getModificationDate().getTime()));
                }
            }
            
            // 페이지 정보 추가
            metadata.put("PageCount", String.valueOf(document.getNumberOfPages()));
            
            // 페이지 크기 정보 추가
            if (document.getNumberOfPages() > 0) {
                PDPage firstPage = document.getPage(0);
                PDRectangle mediaBox = firstPage.getMediaBox();
                metadata.put("PageSize", String.format("%.2f x %.2f pt", mediaBox.getWidth(), mediaBox.getHeight()));
                metadata.put("PageRotation", String.valueOf(firstPage.getRotation()));
            }
            
            // 보안 정보 추가
            metadata.put("Encrypted", String.valueOf(document.isEncrypted()));
        }
        
        return metadata;
    }
    
    /**
     * PDF 파일에 텍스트 워터마크 추가
     * 
     * @param inputFile 입력 PDF 파일
     * @param outputFile 출력 PDF 파일
     * @param watermarkText 워터마크 텍스트
     * @param opacity 투명도 (0.0 ~ 1.0)
     * @return 처리된 PDF 파일
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static File addTextWatermark(File inputFile, File outputFile, String watermarkText, float opacity) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            PDPageTree pages = document.getPages();
            
            for (PDPage page : pages) {
                PDRectangle pageSize = page.getMediaBox();
                float fontSize = Math.min(pageSize.getWidth(), pageSize.getHeight()) / 10; // 페이지 크기에 비례한 폰트 크기

                // Create content stream for this page
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    // 투명도 설정
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(opacity);
                    contentStream.setGraphicsStateParameters(graphicsState);
                    
                    // 텍스트 색상 설정 (회색)
                    contentStream.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                    
                    // 텍스트 상태 및 위치 설정
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize);
                    
                    // 워터마크를 대각선으로 표시하기 위한 회전 변환
                    float centerX = pageSize.getWidth() / 2;
                    float centerY = pageSize.getHeight() / 2;
                    float angle = (float) Math.toRadians(45); // 45도 회전
                    
                    // 중앙에 위치하도록 이동 및 회전
                    AffineTransform at = new AffineTransform();
                    at.translate(centerX, centerY);
                    at.rotate(angle);
                    at.translate(-fontSize * watermarkText.length() / 4, 0);
                    
                    // AffineTransform을 Matrix로 변환
                    Matrix matrix = new Matrix(
                            (float) at.getScaleX(),
                            (float) at.getShearY(),
                            (float) at.getShearX(),
                            (float) at.getScaleY(),
                            (float) at.getTranslateX(),
                            (float) at.getTranslateY()
                    );
                    contentStream.setTextMatrix(matrix);
                    
                    // 워터마크 텍스트 작성
                    contentStream.showText(watermarkText);
                    contentStream.endText();
                }
            }
            
            // 파일 저장
            document.save(outputFile);
        }
        
        return outputFile;
    }
    
    /**
     * PDF 파일 품질 분석
     * 
     * @param pdfFile PDF 파일
     * @return PDF 품질 정보
     * @throws IOException 파일 로드 오류 발생 시
     */
    public static PdfQualityInfo analyzeQuality(File pdfFile) throws IOException {
        long fileSize = pdfFile.length();
        int pageCount = 0;
        int estimatedDpi = 0;
        String qualityLevel;
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            pageCount = document.getNumberOfPages();
            
            // 첫 페이지의 이미지를 분석하여 대략적인 DPI 추정
            if (pageCount > 0) {
                PDPage firstPage = document.getPage(0);
                PDResources resources = firstPage.getResources();
                
                if (resources != null) {
                    // 이미지 객체 분석
                    Iterable<COSName> xObjectNames = resources.getXObjectNames();
                    if (xObjectNames != null) {
                        for (COSName name : xObjectNames) {
                            PDXObject xObject = resources.getXObject(name);
                            if (xObject instanceof PDImageXObject) {
                                PDImageXObject image = (PDImageXObject) xObject;
                                PDRectangle mediaBox = firstPage.getMediaBox();
                                
                                // 이미지 크기와 페이지 크기 비율로 DPI 추정
                                float widthPt = mediaBox.getWidth();
                                float heightPt = mediaBox.getHeight();
                                int imageWidth = image.getWidth();
                                int imageHeight = image.getHeight();
                                
                                // 72 DPI = 1 pt
                                float widthDpi = imageWidth / widthPt * 72;
                                float heightDpi = imageHeight / heightPt * 72;
                                
                                estimatedDpi = Math.round((widthDpi + heightDpi) / 2);
                                break;
                            }
                        }
                    }
                }
            }
            
            // DPI를 기준으로 품질 수준 결정
            if (estimatedDpi > 300) {
                qualityLevel = "고품질";
            } else if (estimatedDpi >= 150) {
                qualityLevel = "중간 품질";
            } else if (estimatedDpi > 0) {
                qualityLevel = "저품질";
            } else {
                // 이미지 없거나 DPI 추정 못한 경우
                qualityLevel = "알 수 없음";
                
                // 페이지당 파일 크기로 대략적인 품질 추정
                long sizePerPage = fileSize / Math.max(1, pageCount);
                
                if (sizePerPage > 1024 * 1024) { // > 1MB per page
                    qualityLevel = "고품질 (크기 기준)";
                    estimatedDpi = 300; // 추정값
                } else if (sizePerPage > 200 * 1024) { // > 200KB per page
                    qualityLevel = "중간 품질 (크기 기준)";
                    estimatedDpi = 150; // 추정값
                } else {
                    qualityLevel = "저품질 (크기 기준)";
                    estimatedDpi = 72; // 추정값
                }
            }
        }
        
        return new PdfQualityInfo(fileSize, pageCount, estimatedDpi, qualityLevel);
    }
    
    /**
     * PDF 파일 요약 정보 반환
     * 
     * @param pdfFile PDF 파일
     * @return 요약 정보 문자열
     * @throws IOException 파일 로드 오류 발생 시
     */
    public static String getSummary(File pdfFile) throws IOException {
        Map<String, String> metadata = extractMetadata(pdfFile);
        PdfQualityInfo qualityInfo = analyzeQuality(pdfFile);
        
        StringBuilder summary = new StringBuilder();
        summary.append("=== PDF 파일 요약 정보 ===\n");
        summary.append(String.format("파일명: %s\n", pdfFile.getName()));
        summary.append(String.format("파일 크기: %s\n", formatFileSize(pdfFile.length())));
        summary.append(String.format("페이지 수: %s\n", metadata.get("PageCount")));
        
        if (metadata.containsKey("Title")) {
            summary.append(String.format("제목: %s\n", metadata.get("Title")));
        }
        
        if (metadata.containsKey("Author")) {
            summary.append(String.format("작성자: %s\n", metadata.get("Author")));
        }
        
        summary.append(String.format("생성 날짜: %s\n", metadata.getOrDefault("CreationDate", "알 수 없음")));
        summary.append(String.format("품질 레벨: %s (예상 DPI: %d)\n", qualityInfo.getQualityLevel(), qualityInfo.getEstimatedDpi()));
        summary.append(String.format("암호화 여부: %s\n", metadata.getOrDefault("Encrypted", "false")));
        
        return summary.toString();
    }
    
    /**
     * 여러 PDF 파일을 병합하고 책갈피 추가
     * 
     * @param outputFile 출력 PDF 파일
     * @param bookmarks 각 파일에 대한 책갈피 이름 배열
     * @param inputFiles 입력 PDF 파일들
     * @return 처리된 PDF 파일
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static File mergeWithBookmarks(File outputFile, String[] bookmarks, File... inputFiles) throws IOException {
        if (inputFiles.length == 0) {
            throw new IllegalArgumentException("병합할 PDF 파일이 없습니다.");
        }
        
        if (bookmarks != null && bookmarks.length != inputFiles.length) {
            throw new IllegalArgumentException("책갈피 배열의 길이는 입력 파일 배열의 길이와 같아야 합니다.");
        }
        
        // 출력 문서 생성
        try (PDDocument mergedDoc = new PDDocument()) {
            // 책갈피 생성을 위한 아웃라인 초기화
            PDDocumentOutline outline = new PDDocumentOutline();
            mergedDoc.getDocumentCatalog().setDocumentOutline(outline);
            
            int startPageIndex = 0;
            
            // 각 파일을 순회하며 병합
            for (int i = 0; i < inputFiles.length; i++) {
                File inputFile = inputFiles[i];
                
                try (PDDocument doc = Loader.loadPDF(inputFile)) {
                    int pageCount = doc.getNumberOfPages();
                    
                    // 책갈피 생성 (제공된 경우)
                    if (bookmarks != null && bookmarks[i] != null && !bookmarks[i].isEmpty()) {
                        PDOutlineItem bookmark = new PDOutlineItem();
                        bookmark.setTitle(bookmarks[i]);
                        outline.addLast(bookmark);
                        
                        // 책갈피가 가리키는 페이지 설정
                        PDPage destPage = doc.getPage(0); // 첫 페이지
                        PDPageFitDestination dest = new PDPageFitDestination();
                        dest.setPage(destPage);
                        bookmark.setDestination(dest);
                    }
                    
                    // 페이지 가져오기 및 병합
                    for (int j = 0; j < pageCount; j++) {
                        PDPage page = doc.getPage(j);
                        PDPage imported = mergedDoc.importPage(page);
                        
                        // 페이지 크기 및 회전 유지
                        imported.setMediaBox(page.getMediaBox());
                        imported.setRotation(page.getRotation());
                    }
                    
                    startPageIndex += pageCount;
                }
            }
            
            // 파일 저장
            mergedDoc.save(outputFile);
        }
        
        return outputFile;
    }
    
    /**
     * 이미지 파일을 PDF로 변환
     * 
     * @param imageFile 입력 이미지 파일
     * @param outputFile 출력 PDF 파일
     * @return 처리된 PDF 파일
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static File convertImageToPdf(File imageFile, File outputFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        
        if (image == null) {
            throw new IOException("이미지 파일을 읽을 수 없습니다: " + imageFile.getName());
        }
        
        try (PDDocument document = new PDDocument()) {
            // 이미지 크기에 맞는 페이지 생성
            PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
            document.addPage(page);
            
            // 이미지를 PDF에 삽입
            PDImageXObject pdImageXObject = PDImageXObject.createFromFileByContent(imageFile, document);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImageXObject, 0, 0, image.getWidth(), image.getHeight());
            }
            
            // 파일 저장
            document.save(outputFile);
        }
        
        return outputFile;
    }
    
    /**
     * PDF 파일의 첫 페이지를 미리보기 이미지로 추출
     * 
     * @param pdfFile PDF 파일
     * @param outputDirectory 출력 디렉토리
     * @param dpi 이미지 해상도 (DPI)
     * @return 미리보기 이미지 파일
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static File extractPreviewImage(File pdfFile, File outputDirectory, int dpi) throws IOException {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        String baseName = pdfFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        File outputFile = new File(outputDirectory, baseName + "_preview.png");
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.getNumberOfPages() > 0) {
                PDFRenderer renderer = new PDFRenderer(document);
                // 첫 페이지만 렌더링
                BufferedImage image = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
                
                // 이미지 저장
                ImageIO.write(image, "PNG", outputFile);
            } else {
                throw new IOException("PDF 파일에 페이지가 없습니다: " + pdfFile.getName());
            }
        }
        
        return outputFile;
    }
    
    /**
     * 디렉토리 내 모든 PDF 파일 일괄 처리
     * 
     * @param inputDir 입력 디렉토리
     * @param outputDir 출력 디렉토리
     * @param processor PDF 프로세서
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static void batchProcess(File inputDir, File outputDir, PdfProcessor processor) throws IOException {
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("유효한 입력 디렉토리가 아닙니다: " + inputDir.getAbsolutePath());
        }
        
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // PDF 파일 목록 가져오기
        File[] pdfFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("처리할 PDF 파일이 없습니다: " + inputDir.getAbsolutePath());
            return;
        }
        
        // 진행 상황 추적을 위한 카운터
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalCount = pdfFiles.length;
        
        // 각 파일 처리
        for (File pdfFile : pdfFiles) {
            try {
                String outputName = pdfFile.getName();
                File outputFile = new File(outputDir, outputName);
                
                // 프로세서로 파일 처리
                processor.process(pdfFile, outputFile);
                
                // 진행 상황 출력
                int current = processedCount.incrementAndGet();
                System.out.printf("처리 중: %d/%d (%.1f%%) - %s%n", 
                        current, totalCount, (current * 100.0 / totalCount), pdfFile.getName());
                
            } catch (Exception e) {
                System.err.println("파일 처리 실패: " + pdfFile.getName() + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * 파일 크기를 읽기 쉬운 형식으로 변환
     * 
     * @param size 파일 크기 (바이트)
     * @return 형식화된 파일 크기 문자열
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * PDF 품질 정보를 담는 클래스
     */
    public static class PdfQualityInfo {
        private final long fileSize;
        private final int pageCount;
        private final int estimatedDpi;
        private final String qualityLevel;
        
        public PdfQualityInfo(long fileSize, int pageCount, int estimatedDpi, String qualityLevel) {
            this.fileSize = fileSize;
            this.pageCount = pageCount;
            this.estimatedDpi = estimatedDpi;
            this.qualityLevel = qualityLevel;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public int getPageCount() {
            return pageCount;
        }
        
        public int getEstimatedDpi() {
            return estimatedDpi;
        }
        
        public String getQualityLevel() {
            return qualityLevel;
        }
        
        @Override
        public String toString() {
            return String.format(
                    "파일 크기: %s, 페이지 수: %d, 예상 DPI: %d, 품질 수준: %s",
                    formatFileSize(fileSize), pageCount, estimatedDpi, qualityLevel
            );
        }
    }
    
    /**
     * PDF 처리를 위한 함수형 인터페이스
     */
    @FunctionalInterface
    public interface PdfProcessor {
        void process(File inputFile, File outputFile) throws IOException;
    }
    
    /**
     * PDF 파일을 지정된 최대 크기(MB) 이하로 분할합니다.
     * 각 분할된 파일은 원본 파일명_001, 원본 파일명_002 등의 형식으로 저장됩니다.
     *
     * @param inputFile PDF 입력 파일
     * @param outputDir 출력 디렉토리
     * @param maxSizeMB 분할된 파일당 최대 크기(MB)
     * @return 분할된 파일 배열
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static File[] splitPdfBySize(File inputFile, File outputDir, int maxSizeMB) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        List<File> splitFiles = new ArrayList<>();
        long maxSizeBytes = maxSizeMB * 1024L * 1024L; // MB -> bytes
        
        String baseName = inputFile.getName();
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        try (PDDocument document = Loader.loadPDF(inputFile)) {
            int totalPages = document.getNumberOfPages();
            System.out.println("총 페이지 수: " + totalPages);
            
            // 먼저 각 페이지의 개별 크기를 측정
            Map<Integer, Long> pageSizes = estimatePageSizes(document, inputFile);
            
            // 페이지를 최대 크기 기준으로 그룹화
            List<List<Integer>> pageGroups = groupPagesBySize(pageSizes, maxSizeBytes);
            
            // 그룹별로 파일 생성
            for (int groupIndex = 0; groupIndex < pageGroups.size(); groupIndex++) {
                List<Integer> pageGroup = pageGroups.get(groupIndex);
                if (pageGroup.isEmpty()) continue;
                
                // 새 문서 생성
                try (PDDocument newDoc = new PDDocument()) {
                    for (int pageIndex : pageGroup) {
                        PDPage importedPage = newDoc.importPage(document.getPage(pageIndex));
                    }
                    
                    // 파일 저장
                    String groupNumber = String.format("%03d", groupIndex + 1);
                    File outputFile = new File(outputDir, baseName + "_" + groupNumber + ".pdf");
                    newDoc.save(outputFile);
                    splitFiles.add(outputFile);
                    
                    System.out.println("파일 저장: " + outputFile.getName() + 
                            ", 페이지: " + pageGroup.size() + 
                            ", 크기: " + formatFileSize(outputFile.length()));
                }
            }
        }
        
        return splitFiles.toArray(new File[0]);
    }
    
    /**
     * PDF 문서의 각 페이지 크기를 추정합니다.
     * 
     * @param document PDF 문서
     * @param pdfFile PDF 파일 (전체 크기 계산용)
     * @return 페이지 인덱스 -> 추정 크기(byte) 맵
     */
    private static Map<Integer, Long> estimatePageSizes(PDDocument document, File pdfFile) throws IOException {
        Map<Integer, Long> pageSizes = new HashMap<>();
        int totalPages = document.getNumberOfPages();
        
        // 전략 1: 페이지별로 개별 PDF 생성하여 크기 측정 (메모리 효율적)
        Path tempDir = Files.createTempDirectory("pdf_page_size_estimation");
        
        for (int i = 0; i < totalPages; i++) {
            try (PDDocument pageDoc = new PDDocument()) {
                PDPage page = document.getPage(i);
                pageDoc.addPage(page);
                
                File pageFile = tempDir.resolve("page_" + i + ".pdf").toFile();
                pageDoc.save(pageFile);
                
                long pageSize = pageFile.length();
                pageSizes.put(i, pageSize);
                
                // 임시 파일 삭제
                Files.deleteIfExists(pageFile.toPath());
            }
        }
        
        // 임시 디렉토리 삭제
        Files.deleteIfExists(tempDir);
        
        return pageSizes;
    }
    
    /**
     * 페이지를 최대 크기 제한에 맞춰 그룹화합니다.
     * 
     * @param pageSizes 페이지별 크기 맵
     * @param maxSizeBytes 그룹당 최대 크기
     * @return 페이지 그룹 리스트
     */
    private static List<List<Integer>> groupPagesBySize(Map<Integer, Long> pageSizes, long maxSizeBytes) {
        List<List<Integer>> groups = new ArrayList<>();
        List<Integer> currentGroup = new ArrayList<>();
        long currentGroupSize = 0;
        
        // 페이지 번호 순서대로 정렬
        List<Integer> pageIndices = new ArrayList<>(pageSizes.keySet());
        Collections.sort(pageIndices);
        
        for (int pageIndex : pageIndices) {
            long pageSize = pageSizes.get(pageIndex);
            
            // 하나의 페이지가 최대 크기보다 크면 별도 처리
            if (pageSize > maxSizeBytes) {
                if (!currentGroup.isEmpty()) {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                    currentGroupSize = 0;
                }
                
                List<Integer> singlePageGroup = new ArrayList<>();
                singlePageGroup.add(pageIndex);
                groups.add(singlePageGroup);
                
                System.out.println("경고: 페이지 " + pageIndex + "의 크기가 최대 제한인 " + 
                        formatFileSize(maxSizeBytes) + "보다 큽니다: " + formatFileSize(pageSize));
                continue;
            }
            
            // 현재 그룹에 페이지를 추가했을 때 최대 크기를 초과하면 새 그룹 시작
            if (currentGroupSize + pageSize > maxSizeBytes && !currentGroup.isEmpty()) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                currentGroupSize = 0;
            }
            
            // 현재 그룹에 페이지 추가
            currentGroup.add(pageIndex);
            currentGroupSize += pageSize;
        }
        
        // 마지막 그룹 추가
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        
        return groups;
    }
    
    /**
     * books 디렉토리 내 모든 PDF 파일을 최대 크기 기준으로 분할하여 처리합니다.
     * 
     * @param booksDir books 디렉토리 경로
     * @param outputRootDir 출력 루트 디렉토리
     * @param maxSizeMB 분할된 파일당 최대 크기(MB)
     * @return 총 처리된 파일 수
     * @throws IOException 파일 처리 오류 발생 시
     */
    public static int splitAllPdfsBySize(File booksDir, File outputRootDir, int maxSizeMB) throws IOException {
        if (!booksDir.exists() || !booksDir.isDirectory()) {
            throw new IllegalArgumentException("유효한 books 디렉토리가 아닙니다: " + booksDir.getAbsolutePath());
        }
        
        if (!outputRootDir.exists()) {
            outputRootDir.mkdirs();
        }
        
        AtomicInteger totalProcessedFiles = new AtomicInteger(0);
        
        // PDF 파일 필터링
        File[] pdfFiles = booksDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        
        if (pdfFiles == null || pdfFiles.length == 0) {
            System.out.println("처리할 PDF 파일이 없습니다: " + booksDir.getAbsolutePath());
            return 0;
        }
        
        for (File pdfFile : pdfFiles) {
            try {
                String baseName = pdfFile.getName();
                if (baseName.toLowerCase().endsWith(".pdf")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                
                // 각 파일별 출력 디렉토리 생성
                File outputDir = new File(outputRootDir, baseName + "_split");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                System.out.println("파일 처리 중: " + pdfFile.getName());
                System.out.println("출력 디렉토리: " + outputDir.getAbsolutePath());
                System.out.println("최대 크기 제한: " + maxSizeMB + "MB");
                
                // 파일 분할 수행
                File[] splitFiles = splitPdfBySize(pdfFile, outputDir, maxSizeMB);
                
                // 처리된 파일 수 추가
                totalProcessedFiles.addAndGet(splitFiles.length);
                
                System.out.println("처리 완료: " + pdfFile.getName() + " -> " + splitFiles.length + "개 파일로 분할됨");
            } catch (Exception e) {
                System.err.println("파일 처리 실패: " + pdfFile.getName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return totalProcessedFiles.get();
    }
} 