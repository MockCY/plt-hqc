package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.DeviceRow;
import com.qinglian.fitness.common.ApiException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class DeviceExcelExportService {

    private static final int LABEL_COLUMN = 3;
    private final DeviceQrCodeService deviceQrCodeService;

    public DeviceExcelExportService(DeviceQrCodeService deviceQrCodeService) {
        this.deviceQrCodeService = deviceQrCodeService;
    }

    public byte[] export(List<DeviceRow> devices) {
        if (devices.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_EXPORT_EMPTY", "请至少选择一台设备");
        }
        if (devices.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_EXPORT_LIMIT", "单次最多导出 100 台设备");
        }
        if (devices.stream().anyMatch(device -> "THIRD_PARTY".equals(device.deviceSource()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_EXPORT_SOURCE_INVALID", "第三方设备不能导出品牌设备标签");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("设备标签");
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, LABEL_COLUMN));
            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 28 * 256);
            sheet.setColumnWidth(2, 24 * 256);
            sheet.setColumnWidth(LABEL_COLUMN, 30 * 256);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle valueStyle = valueStyle(workbook);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(28);
            String[] columns = {"品牌名称", "型号", "SN", "设备标签"};
            for (int column = 0; column < columns.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(columns[column]);
                cell.setCellStyle(headerStyle);
            }

            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            for (int index = 0; index < devices.size(); index++) {
                DeviceRow device = devices.get(index);
                int rowIndex = index + 1;
                Row row = sheet.createRow(rowIndex);
                row.setHeightInPoints(180);
                writeValue(row, 0, brandLabel(device), valueStyle);
                writeValue(row, 1, device.deviceModel(), valueStyle);
                writeValue(row, 2, device.serialNumber(), valueStyle);
                Cell imageCell = row.createCell(LABEL_COLUMN);
                imageCell.setCellStyle(valueStyle);

                byte[] label = deviceQrCodeService.generateDeviceLabel(device);
                int pictureId = workbook.addPicture(label, Workbook.PICTURE_TYPE_PNG);
                XSSFClientAnchor anchor = new XSSFClientAnchor();
                anchor.setCol1(LABEL_COLUMN);
                anchor.setRow1(rowIndex);
                anchor.setCol2(LABEL_COLUMN + 1);
                anchor.setRow2(rowIndex + 1);
                anchor.setAnchorType(org.apache.poi.ss.usermodel.ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                drawing.createPicture(anchor, pictureId);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成设备标签 Excel 失败", exception);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle valueStyle(XSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle borderedStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private void writeValue(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private String brandLabel(DeviceRow device) {
        if (device.brand() != null && !device.brand().isBlank()) return device.brand();
        return device.serialNumber().toUpperCase().startsWith("AVW") ? "Manhart" : "ARVELLO";
    }
}
