# PDF 압축 프로젝트

이 프로젝트는 PDF 파일의 크기를 줄이는 다양한 압축 기능을 제공합니다. 객체지향적인 설계를 통해 확장성과 유지보수성이 좋은 구조로 구현되었으며, 품질 저하 없는 무손실 압축 기능을 포함합니다.

## 주요 기능

- **다양한 PDF 압축 방식 지원**
  - 무손실 압축 (품질 저하 없음, PNG 형식 기반)
  - 구조 보존 최적화 압축 (텍스트 품질 유지, 이미지만 압축)
  - 이미지 변환 기반 압축 (최대 압축률)
- **압축 수준 조정**: LOW, MEDIUM, HIGH, EXTREME 압축 레벨 지원
- **배치 처리**: 디렉토리 내 다중 파일 일괄 압축
- **진행 상태 모니터링**: 옵저버 패턴을 통한 압축 과정 실시간 추적

## 기술 스택

- Java 21
- Spring Boot 3.4.4
- Apache PDFBox 3.0.3 (PDF 처리 라이브러리)
- JUnit 5 & AssertJ (테스트)
- Spring Core (의존성 주입, 설정 관리)

## 압축 방식 비교

| 압축 방식 | 특징 | 품질 | 압축률 | 적합한 사용 사례 |
|---------|------|-----|-------|--------------|
| **무손실 압축** | PDF 구조 유지, PNG 무손실 압축 사용 | 원본과 동일 | 낮음-중간 | 품질이 중요한 문서, 공식 문서 |
| **구조 보존 최적화** | PDF 구조 유지, 이미지만 압축 | 텍스트 원본 유지, 이미지 약간 저하 | 중간-높음 | 일반 문서, 텍스트 위주 PDF |
| **이미지 변환 압축** | 페이지를 이미지로 변환 후 압축 | 전체적으로 저하 | 매우 높음 | 보관용 문서, 웹 공유용 |

## 설치 방법

1. 저장소 클론: `git clone https://github.com/yourusername/compress-pdf-kata.git`
2. 프로젝트 디렉토리로 이동: `cd compress-pdf-kata`
3. 빌드: `./gradlew build`
4. 실행: `./gradlew bootRun`

## 사용 방법

### 명령줄 인터페이스

```bash
# 단일 파일 압축 (기본값: 무손실 압축)
java -jar pdf-compressor.jar compress input.pdf output.pdf MEDIUM

# 압축 방식 지정
java -jar pdf-compressor.jar compress input.pdf output.pdf HIGH --type=lossless
java -jar pdf-compressor.jar compress input.pdf output.pdf MEDIUM --type=optimized
java -jar pdf-compressor.jar compress input.pdf output.pdf LOW --type=image

# 디렉토리 일괄 처리
java -jar pdf-compressor.jar batch input-directory output-directory MEDIUM --type=lossless

# 도움말 표시
java -jar pdf-compressor.jar help
```

### 자바 코드에서 사용

```java
// 무손실 압축기 사용
PdfCompressor compressor = new LosslessPdfCompressor();
CompressionService service = new CompressionService(compressor);

// 단일 파일 압축
File inputFile = new File("input.pdf");
File outputFile = new File("compressed.pdf");
CompressionResult result = service.compress(inputFile, outputFile, CompressionLevel.MEDIUM);

// 압축 결과 확인
System.out.println("원본 크기: " + result.getOriginalSize() + " bytes");
System.out.println("압축 크기: " + result.getCompressedSize() + " bytes");
System.out.println("압축률: " + result.getCompressionRatio() + "%");
System.out.println("처리 시간: " + result.getProcessingTimeMs() + "ms");
```

### 배치 처리 예제

```java
// 압축기 선택 (최적화, 무손실, 이미지 변환 중 택1)
PdfCompressor compressor = new OptimizedPdfCompressor(); // 또는 LosslessPdfCompressor, ImageQualityCompressor

// 배치 프로세서 생성
BatchProcessor batchProcessor = new BatchProcessor(new CompressionService(compressor));

// 진행 상황 로거 등록
batchProcessor.registerObserver(new CompressionProgressLogger());

// 디렉토리 내 모든 PDF 압축
File inputDir = new File("input-directory");
File outputDir = new File("output-directory");
batchProcessor.processBatch(inputDir, outputDir, CompressionLevel.HIGH);
```

### 설정 파일(application.properties)

기본 압축 방식을 설정 파일에서 지정할 수 있습니다:

```properties
# 압축기 설정 (lossless, optimized, image)
pdf.compressor.type=lossless
```

## 구현 아키텍처

이 프로젝트는 다음과 같은 객체지향 설계 원칙을 따릅니다:

- **단일 책임 원칙(SRP)**: 각 압축기 클래스는 특정 압축 방식에 집중
- **개방-폐쇄 원칙(OCP)**: 새로운 압축 알고리즘은 기존 코드 수정 없이 추가 가능
- **의존성 역전 원칙(DIP)**: 구체적인 구현보다 `PdfCompressor` 인터페이스에 의존

### 주요 컴포넌트

```
com.example.pdf
├── compressor
│   ├── PdfCompressor.java           - 압축 인터페이스
│   ├── LosslessPdfCompressor.java   - 무손실 압축 구현
│   ├── OptimizedPdfCompressor.java  - 최적화 압축 구현 
│   └── ImageQualityCompressor.java  - 이미지 변환 압축 구현
├── model
│   ├── CompressionLevel.java        - 압축 수준 열거형
│   └── CompressionResult.java       - 압축 결과 모델
├── service
│   └── CompressionService.java      - 압축 서비스
├── batch
│   └── BatchProcessor.java          - 배치 처리 로직
├── observer
│   ├── CompressionObserver.java     - 옵저버 인터페이스
│   └── CompressionProgressLogger.java - 로깅 구현체
└── config
    └── CompressorConfig.java        - 압축기 설정/선택 로직
```

## 테스트

프로젝트는 다양한 테스트 케이스를 포함하고 있습니다:

### 테스트 실행 방법

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests LosslessPdfCompressorTest
./gradlew test --tests LosslessPdfCompressorRealFileTest
```

### 실제 파일 테스트

실제 PDF 파일을 사용한 테스트 방법:

1. `books` 디렉토리를 생성 (또는 이미 존재하는 디렉토리 사용)
2. 테스트할 PDF 파일을 `books` 디렉토리에 복사
3. `LosslessPdfCompressorRealFileTest` 테스트 클래스 실행
4. 압축 결과는 `test-output` 디렉토리에 생성됨

```java
// LosslessPdfCompressorRealFileTest는 다음과 같은 테스트를 수행합니다:
void testRealFileLosslessCompression() // 모든 PDF 파일에 대해 무손실 압축
void testCompressionLevels()           // 다양한 압축 레벨 테스트
void testComparisonWithOtherCompressors() // 다른 압축 방식과 비교
```

#### 필요한 디렉토리 구조:
```
프로젝트 루트
├── books/             - 원본 PDF 파일 위치
│   ├── document1.pdf
│   └── document2.pdf
└── test-output/       - 압축 결과 저장 위치 (자동 생성)
```

### 자동 생성 테스트

`RealPdfCompressionTest` 클래스는 테스트용 PDF 파일을 자동으로 생성하여 테스트합니다:

- 텍스트만 포함된 PDF
- 이미지만 포함된 PDF
- 텍스트와 이미지가 모두 포함된 복합 PDF

## 압축 방식 성능 비교

실제 PDF 파일 테스트 결과에 기반한 압축률 비교:

| 문서 유형 | 무손실 압축 | 구조 보존 최적화 | 이미지 변환 압축 |
|---------|------------|--------------|--------------|
| 텍스트 위주 | 5-15% | 15-30% | 50-70% |
| 이미지 위주 | 20-40% | 50-80% | 70-90% |
| 복합 문서 | 10-30% | 40-70% | 60-85% |

### 압축 속도 비교 (1MB 문서 기준)
- **무손실 압축**: 300-800ms
- **구조 보존 최적화**: 500-1200ms
- **이미지 변환 압축**: 1000-2500ms

## 구현 세부사항

### 무손실 압축 방식 구현
`LosslessPdfCompressor`는 다음과 같은 방식으로 무손실 압축을 구현합니다:

1. PDF 문서 구조를 그대로 유지
2. 내부 이미지에 대해 LosslessFactory를 사용한 PNG 기반 무손실 압축 적용
3. 텍스트, 벡터 그래픽 등 다른 요소는 수정하지 않음
4. 불필요한 메타데이터 최적화

```java
// 핵심 무손실 압축 로직
PDImageXObject losslessImage = LosslessFactory.createFromImage(document, bufferedImage);
resources.put(name, losslessImage);
```

## 알려진 이슈 및 제한사항

- 매우 복잡한 그래픽이 포함된 PDF 파일은 처리 시간이 길어질 수 있음
- 암호화된 PDF 파일은 먼저 암호 해제 후 사용해야 함
- PDFBox 3.0.3의 일부 기능이 완전히 안정화되지 않음

## 향후 계획

- 멀티스레드 병렬 처리 지원
- 웹 인터페이스 추가
- AI 기반 최적 압축 방식 자동 선택 기능
- ICC 프로파일 최적화 기능 추가

## 기여 방법

1. 저장소를 포크(Fork)합니다
2. 기능 브랜치를 생성합니다 (`git checkout -b feature/amazing-feature`)
3. 변경사항을 커밋합니다 (`git commit -m 'Add some amazing feature'`)
4. 브랜치에 푸시합니다 (`git push origin feature/amazing-feature`)
5. 풀 리퀘스트를 생성합니다

## 라이센스

MIT 