package com.edumate.common.enums;

/**
 * 支持的文档类型
 */
public enum DocumentType {
    PDF("pdf"),
    PPTX("pptx"),
    PPT("ppt"),
    DOCX("docx"),
    DOC("doc"),
    XLSX("xlsx"),
    XLS("xls"),
    TXT("txt"),
    MARKDOWN("md");

    private final String extension;

    DocumentType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    /** 根据文件扩展名推断文档类型 */
    public static DocumentType fromExtension(String extension) {
        for (DocumentType type : values()) {
            if (type.extension.equalsIgnoreCase(extension)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的文档格式: ." + extension);
    }
}
