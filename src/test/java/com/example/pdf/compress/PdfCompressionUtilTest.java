package com.example.pdf.compress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfCompressionUtilTest {
  @DisplayName("PDF 압축 테스트")
  @Test
  void compressPdf_ShouldReduceFileSizeSample(@TempDir Path tempDir) throws IOException {
    // given
    String MEDIA_TYPE_PDF = ".pdf";
    String fileName = "가상 면접 사례로 배우는 대규모 시스템 설계 기초2";
    File inputFile = new File("src/test/resources/books/" + fileName + MEDIA_TYPE_PDF);
    File outputFile = tempDir.resolve("compressed" + MEDIA_TYPE_PDF).toFile();

    // when
    long compressedSize = PdfCompressionUtil.compressPdf(inputFile, outputFile);

    // then
    assertThat(outputFile).exists();
    assertThat(compressedSize).isLessThan(inputFile.length());
  }

  @DisplayName(value = "PDF 압축 실 진행 테스트")
  @Test
  void compressPdf_ShouldReduceFileSize() throws IOException {
    // given
    String MEDIA_TYPE_PDF = ".pdf";
    String fileName = "가상 면접 사례로 배우는 대규모 시스템 설계 기초2";
    File inputFile = new File("src/test/resources/books/" + fileName + MEDIA_TYPE_PDF);
    File outputFile = new File("src/test/resources/books/" + fileName + "_압축" + MEDIA_TYPE_PDF);

    // when
    long compressedSize = PdfCompressionUtil.compressPdf(inputFile, outputFile);

    // then
    assertThat(outputFile).exists();
    assertThat(compressedSize).isLessThan(inputFile.length());
  }

}