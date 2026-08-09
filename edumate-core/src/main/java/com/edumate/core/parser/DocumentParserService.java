package com.edumate.core.parser;

import com.edumate.common.enums.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFGroupShape;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析服务 —— 将多种格式文档提取为纯文本
 * 支持: PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX, TXT, MD
 */
@Service
public class DocumentParserService {

    public String parse(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String extension = getExtension(fileName);
        DocumentType type = DocumentType.fromExtension(extension);

        return switch (type) {
            case TXT, MARKDOWN -> Files.readString(filePath);
            case PDF -> parsePdf(filePath);
            case DOCX -> parseDocx(filePath);
            case DOC -> parseDoc(filePath);
            case PPTX -> parsePptx(filePath);
            case PPT -> parsePpt(filePath);
            case XLSX -> parseXlsx(filePath);
            case XLS -> parseXls(filePath);
        };
    }

    // ==================== PDF ====================

    private String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    // ==================== DOCX (Word 2007+) ====================

    private String parseDocx(Path filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();

            // 1. 正文段落
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }

            // 2. 表格内容
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            sb.append(cellText.trim()).append("\t");
                        }
                    }
                    sb.append("\n");
                }
            }

            // 3. 页眉
            for (int i = 0; i < document.getHeaderList().size(); i++) {
                String headerText = document.getHeaderList().get(i).getText();
                if (headerText != null && !headerText.isBlank()) {
                    sb.append(headerText).append("\n");
                }
            }

            // 4. 页脚
            for (int i = 0; i < document.getFooterList().size(); i++) {
                String footerText = document.getFooterList().get(i).getText();
                if (footerText != null && !footerText.isBlank()) {
                    sb.append(footerText).append("\n");
                }
            }

            return sb.toString();
        }
    }

    // ==================== DOC (Word 97-2003) ====================

    private String parseDoc(Path filePath) throws IOException {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(filePath))) {
            WordExtractor extractor = new WordExtractor(document);
            return extractor.getText();
        }
    }

    // ==================== PPTX (PowerPoint 2007+) ====================

    private String parsePptx(Path filePath) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(filePath))) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    extractPptxShapeText(shape, sb);
                }
            }
            return sb.toString();
        }
    }

    // ==================== PPT (PowerPoint 97-2003) ====================

    private String parsePpt(Path filePath) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(Files.newInputStream(filePath));
             HSLFSlideShow ppt = new HSLFSlideShow(fs)) {
            StringBuilder sb = new StringBuilder();
            for (HSLFSlide slide : ppt.getSlides()) {
                for (HSLFShape shape : slide.getShapes()) {
                    extractPptShapeText(shape, sb);
                }
            }
            return sb.toString();
        }
    }

    /**
     * 递归提取 PPT 形状中的文本（含表格和组合形状）
     * HSLFTable 继承自 HSLFGroupShape，递归组形状处理已自动覆盖表格
     */
    private void extractPptShapeText(HSLFShape shape, StringBuilder sb) {
        if (shape instanceof HSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n");
            }
        } else if (shape instanceof HSLFGroupShape group) {
            for (HSLFShape child : group.getShapes()) {
                extractPptShapeText(child, sb);
            }
        }
    }

    /**
     * 递归提取 PPTX 形状中的文本（含表格和组合形状）
     * XSLFTable 不继承 XSLFGroupShape，需单独处理行/单元格
     */
    private void extractPptxShapeText(XSLFShape shape, StringBuilder sb) {
        if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n");
            }
        } else if (shape instanceof XSLFTable table) {
            for (org.apache.poi.xslf.usermodel.XSLFTableRow row : table.getRows()) {
                for (XSLFTableCell cell : row.getCells()) {
                    String text = cell.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text.trim()).append("\t");
                    }
                }
                sb.append("\n");
            }
        } else if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                extractPptxShapeText(child, sb);
            }
        }
    }

    // ==================== XLSX (Excel 2007+) ====================

    private String parseXlsx(Path filePath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(filePath))) {
            return extractWorkbookText(workbook);
        }
    }

    // ==================== XLS (Excel 97-2003) ====================

    private String parseXls(Path filePath) throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook(Files.newInputStream(filePath))) {
            return extractWorkbookText(workbook);
        }
    }

    // ==================== 公共工具方法 ====================

    /**
     * 从 Excel Workbook 中提取纯文本（适用于 HSSF 和 XSSF）
     * 每个 Sheet 以 [Sheet名] 标题开头，单元格用 Tab 分隔，行用换行分隔
     */
    private String extractWorkbookText(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (sheetName != null && !sheetName.isBlank()) {
                sb.append("[").append(sheetName).append("]\n");
            }
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellValue = formatter.formatCellValue(cell);
                    if (cellValue != null && !cellValue.isBlank()) {
                        sb.append(cellValue).append("\t");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("无法识别的文件类型: " + fileName);
        }
        return fileName.substring(dotIndex + 1);
    }
}
