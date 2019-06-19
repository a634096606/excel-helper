package com.github.dreamroute.excel.helper.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import com.github.dreamroute.excel.helper.annotation.Column;
import com.github.dreamroute.excel.helper.annotation.DateColumn;
import com.github.dreamroute.excel.helper.cache.CacheFactory;
import com.github.dreamroute.excel.helper.exception.ExcelHelperException;
import org.apache.poi.util.StringUtil;

/**
 * @author 342252328@qq.com
 */
public class DataAssistant {

    /**
     * 错误常量值
     */
    static final String ERROR_CONSTANTS = "这是一条错误数据";

    private DataAssistant() {
    }

    public static List<List<Object>> createData(Collection<?> sheetData) {

        List<List<Object>> result = new ArrayList<>();

        Class<?> dataCls = sheetData.iterator().next().getClass();
        List<Field> fields = CacheFactory.findFields(dataCls);
        Iterator<?> dataIterator = sheetData.iterator();
        while (dataIterator.hasNext()) {
            List<Object> rowData = new ArrayList<>(fields.size());
            Object data = dataIterator.next();
            for (Field field : fields) {
                try {
                    Object value = field.get(data);
                    // when cell is null, pre process it, avoid NPE.
                    if (Objects.isNull(value)) {
                        CellType ct = field.getAnnotation(Column.class).cellType();
                        if (ct == CellType.STRING) {
                            value = "";
                        } else if (ct == CellType.NUMERIC) {
                            value = 0;
                        } else if (ct == CellType.BOOLEAN) {
                            value = false;
                        }
                    } else {
                        // 处理日期类型
                        if (field.isAnnotationPresent(DateColumn.class)) {
                            DateColumn dc = field.getAnnotation(DateColumn.class);
                            String originalDateFormate = dc.originalDateFormate();
                            String date = (String) value;
                            value = new SimpleDateFormat(originalDateFormate).parse(date);
                        }

                    }
                    rowData.add(value);
                } catch (IllegalArgumentException | IllegalAccessException | ParseException e) {
                    throw new ExcelHelperException(e);
                }
            }
            result.add(rowData);
        }
        return result;
    }

    public static <T> BaseResponse<T> createDataFromSheet(Sheet sheet, Class<T> cls) {
        Iterator<Row> rows = sheet.rowIterator();
        return createDateFromSheet(rows, cls);
    }

    private static <T> BaseResponse<T> createDateFromSheet(Iterator<Row> rows, Class<T> cls) {
        Map<Integer, HeaderInfo> headerInfoMap = proceeHeaderInfo(rows, cls);
        List<T> data = new ArrayList<>();
        StringBuffer errorMessage=new StringBuffer();
        //行数
        int lineNumber = 2;
        while (rows.hasNext()) {
            Row row = rows.next();
            Iterator<Cell> cells = row.cellIterator();
            Constructor<T> c = null;
            try {
                c = cls.getDeclaredConstructor();
            } catch (NoSuchMethodException | SecurityException e) {
                throw new ExcelHelperException("Domain: " + cls.getSimpleName() + " has no default constructor.");
            }
            T domain;
            try {
                domain = c.newInstance();
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
                throw new ExcelHelperException(e1);
            }
            //列数
            int columnNumber = 1;
            while (cells.hasNext()) {
                Cell cell = cells.next();
                HeaderInfo headerInfo = headerInfoMap.get(cell.getColumnIndex());
                Field field = headerInfo.getField();
                try {
                    Object cellValue = getCellValue(cell, headerInfo.getCellType(), field);
                    field.set(domain, cellValue);
                } catch (Exception e) {
                    errorMessage.append("第").append(lineNumber).append("行的第").append(columnNumber).append("列数据错误;");
                }
                columnNumber++;
            }
            data.add(domain);
            lineNumber++;
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            return new BaseResponse<>(-1, errorMessage.toString(), data);
        } else {
            return new BaseResponse<>(data);
        }
    }

    private static Object getCellValue(Cell cell, CellType cellType, Field field) {
        CellType ct = cell.getCellType();
        Object cellValue = null;
        Class<?> type = field.getType();
        // 由于通过cell.getCellTypeEnum()获取的类型和实体定义的类型可能不一致，所以这里以实体类型为准而不能以cell.getCellTypeEnum()为准，进行强转，否则调用field.set()时候报类型错误
        if (cellType == CellType.STRING) {
            if (ct != CellType.STRING) {
                cell.setCellType(CellType.STRING);
            }
            cellValue = getCellValue(cell.getStringCellValue(), type);
        } else if (cellType == CellType.NUMERIC) {
            cellValue = getCellValue(cell.getNumericCellValue(), type);
        } else if (cellType == CellType.BOOLEAN) {
            cellValue = getCellValue(cell.getBooleanCellValue(), type);
        }
        return cellValue;
    }

    private static Object getCellValue(Object cellValue, Class<?> type) {
        String cv = cellValue.toString();
        Object value = null;
        if (StringUtils.isNotBlank(cv)) {
            if (Objects.equals(type, Integer.class) || Objects.equals(type, int.class)) {
                value = Integer.valueOf(new BigDecimal(cv).toPlainString());
            } else if (Objects.equals(type, Float.class) || Objects.equals(type, float.class)) {
                value = Float.valueOf(new BigDecimal(cv).toPlainString());
            } else if (Objects.equals(type, Long.class) || Objects.equals(type, long.class)) {
                value = Long.valueOf(new BigDecimal(cv).toPlainString());
            } else if (Objects.equals(type, Short.class) || Objects.equals(type, short.class)) {
                value = Short.valueOf(new BigDecimal(cv).toPlainString());
            } else if (Objects.equals(type, Character.class) || Objects.equals(type, char.class)) {
                value = Character.valueOf(cv.toCharArray()[0]);
            } else if (Objects.equals(type, Double.class) || Objects.equals(type, double.class)) {
                value = Double.valueOf(new BigDecimal(cv).toPlainString());
            } else if (Objects.equals(type, Boolean.class) || Objects.equals(type, boolean.class)) {
                value = Boolean.valueOf(cv);
            } else if (Objects.equals(type, BigDecimal.class)) {
                value = new BigDecimal(cv);
            } else if (Objects.equals(type, String.class)) {
                value = cv;
            }
        }
        return value;
    }

    public static void main(String[] args) {
        new BigDecimal("").intValue();
    }

    public static Map<Integer, HeaderInfo> proceeHeaderInfo(Iterator<Row> rows, Class<?> cls) {
        if (rows.hasNext()) {
            Row header = rows.next();
            return CacheFactory.findHeaderInfo(cls, header);
        }
        return new HashMap<>(0);
    }

}
