# PDF 압축 및 관리 라이브러리

PDF 파일의 압축, 분할, 병합 및 기타 관리 기능을 제공하는 Java 라이브러리입니다. 대용량 PDF 파일을 효율적으로 처리하고 다양한 최적화 기능을 제공합니다.

## 주요 기능

### PDF 압축 기능
- 다양한 압축 프로필을 통한 PDF 최적화
- 이미지 품질과 DPI 설정 조정을 통한 세밀한 압축률 제어
- 파일 크기 기반 자동 분할 기능

### PDF 페이지 관리
- PDF 페이지 분할 및 추출
- 개별 페이지 분리 및 저장
- 여러 PDF 파일 병합 및 책갈피 추가

### PDF 메타데이터 및 분석
- PDF 메타데이터 추출 및 분석
- 파일 품질 및 특성 평가
- PDF 요약 정보 생성

### 기타 유틸리티 기능
- PDF에 워터마크 추가
- 미리보기 이미지 생성
- 이미지를 PDF로 변환

## 시스템 요구사항

- Java 11 이상
- Apache PDFBox 3.0.x

## 사용 방법

### PDF 파일 압축하기

```java
// 기본 압축 수행
File inputFile = new File("input.pdf");
File outputFile = new File("compressed.pdf");
PdfCompressionUtil.compressPdf(inputFile, outputFile, PdfCompressionUtil.CompressionProfile.BALANCED);

// 커스텀 DPI 설정으로 압축
PdfCompressionUtil.compressPdfWithDpi(inputFile, outputFile, 300, 150);
```

### PDF 파일 분할하기

```java
// 파일 크기 기준 분할 (200MB 이하)
File[] splitFiles = PdfFileManager.splitPdfBySize(inputFile, outputDir, 200);

// 모든 PDF 파일 일괄 분할
int processedFiles = PdfFileManager.splitAllPdfsBySize(inputDir, outputDir, 200);
```

### 페이지 관리 예제

```java
// PDF 파일의 특정 페이지 추출
File rangeFile = PdfFileManager.extractPageRange(pdfFile, outputFile, 5, 10);

// PDF 여러 파일 병합 및 책갈피 추가
String[] bookmarks = {"첫 번째 문서", "두 번째 문서"};
File mergedFile = PdfFileManager.mergeWithBookmarks(outputFile, bookmarks, file1, file2);
```

### 메타데이터 및 분석

```java
// PDF 메타데이터 추출
Map<String, String> metadata = PdfFileManager.extractMetadata(pdfFile);

// PDF 품질 분석
PdfFileManager.PdfQualityInfo qualityInfo = PdfFileManager.analyzeQuality(pdfFile);

// PDF 요약 정보
String summary = PdfFileManager.getSummary(pdfFile);
```

## 압축 프로필

라이브러리는 다양한 압축 프로필을 제공합니다:

| 프로필 | 설명 | 예상 압축률 |
|--------|------|------------|
| MINIMAL_COMPRESSION | 원본과 거의 동일, 불필요한 데이터만 제거 | 1-5% |
| LIGHT_COMPRESSION | 원본과 매우 유사, 육안으로 구분 어려운 수준 | 5-10% |
| MEDIUM_COMPRESSION | 품질과 파일 크기의 균형 | 45-55% |
| BALANCED | 양호한 품질, 일반 용도로 충분 | 50-60% |
| SMALL_SIZE | 웹 공유 및 화면 표시용 | 70-80% |
| TARGET_SIZE_200MB | 300MB 파일을 약 200MB로 압축 | 33-40% |

## 테스트

프로젝트에는 실제 데이터를 사용한 다양한 테스트가 포함되어 있습니다:

- 실제 PDF 파일 정보 분석 및 요약 테스트
- 워터마크 추가 테스트
- PDF 파일 분할 테스트
- 미리보기 이미지 생성 테스트
- 파일 병합 및 책갈피 추가 테스트

테스트 실행:

```bash
./mvnw test
```

## 라이선스

이 프로젝트는 Apache License 2.0에 따라 라이선스가 부여됩니다.