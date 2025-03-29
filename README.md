# PDF 압축 유틸리티

이 라이브러리는 고품질을 유지하면서 PDF 파일 크기를 효과적으로 줄이는 유틸리티입니다. 다양한 압축 프로필을 제공하여 사용 목적에 따라 최적의 결과를 얻을 수 있습니다.

## 주요 기능

- 다양한 압축 프로필을 통한 맞춤형 PDF 압축
- 이미지 해상도, 품질, DPI 조정을 통한 최적화
- 메타데이터 최적화
- 벤치마크 기능으로 압축 효율성 측정
- 원본 파일 보존 (원본과 별도로 압축 파일 생성)
- 배치 처리 지원 (여러 PDF 파일 일괄 압축)

## 압축 프로필

라이브러리는 다양한 사용 사례에 맞게 최적화된 여러 압축 프로필을 제공합니다:

| 프로필 | 이미지 품질 | 큰 이미지 품질 | 최대 DPI | 최소 DPI | 예상 압축률 | 품질 설명 |
|-------|-----------|--------------|---------|---------|-----------|---------|
| **MINIMUM_SIZE** | 0.65 | 0.6 | 300 | 72 | 85-95% | 낮은 품질, 웹 공유 및 화면 표시용 |
| **SMALL_SIZE** | 0.85 | 0.75 | 450 | 120 | 70-80% | 적절한 품질, 텍스트 가독성 유지 |
| **BALANCED** | 0.92 | 0.85 | 600 | 150 | 50-60% | 양호한 품질, 일반 용도로 충분 |
| **TARGET_SIZE_200MB** | 0.95 | 0.90 | 800 | 180 | 33-40% | 좋은 품질, 300MB 파일을 약 200MB로 압축 |
| **HIGH_QUALITY** | 0.98 | 0.95 | 900 | 200 | 30-40% | 매우 좋은 품질, 세밀한 부분에서만 약간의 차이 |
| **MAXIMUM_QUALITY** | 1.0 | 0.99 | 1200 | 300 | 10-20% | 원본과 거의 동일한 품질, 육안으로 구분 불가 |
| **ULTRA_HIGH_QUALITY** | 1.0 | 1.0 | 2400 | 600 | 5-10% | 원본보다 더 높은 해상도, 고품질 인쇄물 수준 |
| **CUSTOM** | 사용자 정의 | 사용자 정의 | 사용자 정의 | 사용자 정의 | 설정에 따라 다름 | 사용자 정의 설정에 따라 결정됨 |

기본 프로필은 `TARGET_SIZE_200MB`로 설정되어 있으며, 약 300MB 크기의 파일을 200MB 정도로 압축하는 것을 목표로 합니다.

## 사용 방법

### 기본 압축

```java
// 파일 압축 (기본 프로필 사용)
File inputFile = new File("input.pdf");
File outputFile = new File("output.pdf");
PdfCompressionUtil.compressPdf(inputFile, outputFile);
```

### 특정 프로필 사용

```java
// 고품질 프로필 사용
PdfCompressionUtil.compressPdfWithProfile(
    inputFile, 
    outputFile, 
    PdfCompressionUtil.CompressionProfile.HIGH_QUALITY
);

// 최소 크기 프로필 사용 (웹 공유용)
PdfCompressionUtil.compressPdfWithProfile(
    inputFile, 
    outputFile, 
    PdfCompressionUtil.CompressionProfile.MINIMUM_SIZE
);
```

### 접미사가 붙은 압축 파일 생성

```java
// 원본 파일명에 "_압축" 접미사를 붙여 동일 폴더에 저장
// 예: sample.pdf -> sample_압축.pdf
PdfCompressionUtil.compressPdfWithPostfix(inputFile);
```

### 사용자 정의 압축 설정

```java
// 커스텀 압축 설정 사용
PdfCompressionUtil.compressPdfWithCustomSettings(
    inputFile,
    outputFile,
    0.95f,     // 이미지 품질
    0.9f,      // 큰 이미지 품질
    1000,      // 최대 DPI
    200,       // 최소 DPI
    5000000    // 무손실 압축 최대 픽셀 수
);
```

### 여러 파일 일괄 처리

```java
// 여러 PDF 파일 일괄 압축
File[] files = {
    new File("file1.pdf"),
    new File("file2.pdf"),
    new File("file3.pdf")
};
Map<String, PdfCompressionUtil.CompressionResult> results = 
    PdfCompressionUtil.compressMultipleFilesWithPostfix(files);

// 결과 확인
for (Map.Entry<String, PdfCompressionUtil.CompressionResult> entry : results.entrySet()) {
    PdfCompressionUtil.CompressionResult result = entry.getValue();
    System.out.println(
        entry.getKey() + ": " + 
        result.getOriginalSize() + " -> " + 
        result.getCompressedSize() + " (" + 
        result.getCompressionRatio() + "%)"
    );
}
```

### 벤치마크 실행

```java
// 디렉토리 내 모든 PDF 파일에 대한 압축 벤치마크 실행
String inputDirectory = "/path/to/pdf/files";
String outputDirectory = "/path/to/compressed/output";
Map<String, PdfCompressionUtil.CompressionResult> benchmarkResults = 
    PdfCompressionUtil.runBenchmark(inputDirectory, outputDirectory);
```

## 시스템 요구사항

- Java 11 이상
- Apache PDFBox 3.0.1 이상
- SLF4J (로깅용)

## 메모리 고려사항

대용량 PDF 파일이나 고해상도 이미지가 많은 PDF 파일을 처리할 때는 충분한 메모리를 할당해야 합니다. JVM 힙 크기를 조정하는 것이 좋습니다:

```
java -Xmx4g -jar your-application.jar
```

## 한계

- 텍스트 기반 압축은 제한적입니다. 이 라이브러리는 주로 이미지가 많은 PDF 파일 압축에 최적화되어 있습니다.
- 디지털 서명된 PDF는 압축 후 서명이 무효화될 수 있습니다.
- PDF/A 규격 문서는 압축 후 규격을 만족하지 않을 수 있습니다.