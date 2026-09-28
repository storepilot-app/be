package com.be.global.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

public final class ExcelCellReader {
    private ExcelCellReader() {
    }

    public static String readTrimmed(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex < 0) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }
}
