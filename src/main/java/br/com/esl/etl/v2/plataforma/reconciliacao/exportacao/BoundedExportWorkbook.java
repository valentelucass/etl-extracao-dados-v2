package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** One-sheet XLSX reader. Complete ZIP/worksheet traversal precedes sealing the occurrence bag. */
public final class BoundedExportWorkbook {
    public record NativeRow(
            int physicalRow, List<ExactExportTable.Atom> cells, long physicalMask) {}

    public record NativeTable(String epoch, String sheet, List<NativeRow> rows) {}

    public record CapturedRow(
            String sheet,
            int physicalRow,
            String epoch,
            Map<Integer, String> headers,
            Map<Integer, ExactExportTable.Atom> cells) {}

    private java.util.function.Consumer<CapturedRow> capturedRows;
    private Map<Integer, String> capturedHeaders = Map.of();

    /** Actual API workbook, including every physical column; existing scope/EOF/bounds remain. */
    public static void scanCapturedFretes(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget,
            final LocalDate start,
            final LocalDate end,
            final java.util.function.Consumer<CapturedRow> sink)
            throws IOException, XMLStreamException {
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final var sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        workbook.capturedRows = java.util.Objects.requireNonNull(sink);
        workbook.rows(sheet, columns, true, null, start, end, null);
    }

    public static void verifyFretes(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget,
            final LocalDate start,
            final LocalDate end)
            throws java.io.IOException, XMLStreamException {
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final var sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        workbook.rows(sheet, columns, true, null, start, end, null);
    }

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private final Map<String, byte[]> parts = new HashMap<>();
    private final ExportValidationSupport.Budget budget;
    private final List<String> strings = new ArrayList<>();
    private final List<String> styles = new ArrayList<>();
    private String epoch = "1900";
    private long physicalCells;

    private BoundedExportWorkbook(final byte[] bytes, final ExportValidationSupport.Budget budget)
            throws IOException {
        this.budget = budget;
        require(bytes.length >= 22, "EXPORT_ZIP_EOF");
        int footer = -1;
        for (int offset = bytes.length - 22;
                offset >= Math.max(0, bytes.length - 65557);
                offset--) {
            if (bytes[offset] == 0x50
                    && bytes[offset + 1] == 0x4b
                    && bytes[offset + 2] == 5
                    && bytes[offset + 3] == 6) {
                final var candidate =
                        ByteBuffer.wrap(bytes, offset, bytes.length - offset)
                                .order(ByteOrder.LITTLE_ENDIAN);
                candidate.position(offset + 20);
                if (offset + 22 + Short.toUnsignedInt(candidate.getShort()) == bytes.length) {
                    footer = offset;
                    break;
                }
            }
        }
        require(footer >= 0, "EXPORT_ZIP_EOF");
        final var end = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(
                end.getShort(footer + 4) == 0 && end.getShort(footer + 6) == 0,
                "EXPORT_ZIP_SINGLE_DISK");
        final int declaredEntries = Short.toUnsignedInt(end.getShort(footer + 10));
        require(
                Integer.toUnsignedLong(end.getInt(footer + 16))
                                + Integer.toUnsignedLong(end.getInt(footer + 12))
                        == footer,
                "EXPORT_ZIP_DIRECTORY_BOUND");
        long unpacked = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                budget.check();
                require(parts.size() < 32 && !entry.isDirectory(), "EXPORT_ZIP_PART_BOUND");
                final String name = entry.getName();
                require(
                        !name.contains("..") && !name.startsWith("/") && !name.contains("\\"),
                        "EXPORT_ZIP_PATH");
                final byte[] content = zip.readNBytes(67108865);
                unpacked += content.length;
                require(
                        content.length <= 67108864 && unpacked <= 100663296,
                        "EXPORT_ZIP_SIZE_BOUND");
                require(parts.putIfAbsent(name, content) == null, "EXPORT_ZIP_DUPLICATE");
                zip.closeEntry(); // Validates EOF and CRC, including unconsumed auxiliary parts.
            }
        }
        require(declaredEntries == parts.size(), "EXPORT_ZIP_ENTRIES_EOF");
        require(parts.containsKey("[Content_Types].xml"), "EXPORT_XLSX_REQUIRED");
    }

    public static void project(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final boolean api,
            final ExactExportTable sink,
            final ExportValidationSupport.Budget budget,
            final LocalDate periodStart,
            final LocalDate periodEnd)
            throws IOException, XMLStreamException {
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final String sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        workbook.rows(sheet, columns, api, sink, periodStart, periodEnd, null);
        sink.seal();
    }

    /** Complete 31-column native observation used by the Coletas presentation adapter. */
    public static NativeTable readCommonRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        require(columns.size() == 31, "COLETAS_NATIVE_EXACT31");
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final String sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        final List<NativeRow> rows = new ArrayList<>();
        workbook.rows(
                sheet,
                columns,
                false,
                null,
                null,
                null,
                row -> {
                    require(rows.size() < 12000, "COLETAS_NATIVE_ROW_BOUND");
                    rows.add(row);
                });
        return new NativeTable(workbook.epoch, sheet, List.copyOf(rows));
    }

    /** Complete Cotações37 observation. Its requested-at period is checked by its own adapter. */
    public static NativeTable readCotacoesRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 37, "COTACOES");
    }

    /** Complete Inventário26 observation; its own adapter checks the started-at period. */
    public static NativeTable readInventarioRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 26, "INVENTARIO");
    }

    /** Complete Sinistros44 observation; its own adapter checks the opening civil date. */
    public static NativeTable readSinistrosRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 44, "SINISTROS");
    }

    /** Complete physical rows; this route does not infer an accounting-debit identity. */
    public static NativeTable readContasPagarRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 28, "CONTAS_PAGAR");
    }

    /** Native Localizador26, explicitly declared after the official UI binding observation. */
    public static NativeTable readLocalizadorRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 26, "LOCALIZADOR");
    }

    /** Native4924: complete52 coordinates, with mapping and presentation pinned by its caller. */
    public static NativeTable readFaturasClienteRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        return readCoordinateNativeRows(bytes, columns, budget, 52, "FATURAS_CLIENTE");
    }

    private static NativeTable readCoordinateNativeRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget,
            final int width,
            final String entity)
            throws IOException, XMLStreamException {
        require(columns != null && columns.size() == width, entity + "_NATIVE_EXACT" + width);
        final var declared = List.copyOf(columns);
        for (int index = 0; index < declared.size(); index++) {
            require(
                    declared.get(index).apiColumn() == index + 1
                            && declared.get(index).uiColumn() == index + 1,
                    entity + "_ALL" + width + "_COORDINATES");
        }
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final String sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        final List<NativeRow> rows = new ArrayList<>();
        workbook.rows(
                sheet,
                declared,
                false,
                null,
                null,
                null,
                row -> {
                    require(rows.size() < 12000, entity + "_NATIVE_ROW_BOUND");
                    rows.add(row);
                });
        return new NativeTable(workbook.epoch, sheet, List.copyOf(rows));
    }

    /**
     * Complete Manifestos91 observation, keyed by physical coordinate rather than title or long
     * mask. Service-date scope requires its own control; this route does not apply the Fretes
     * column36 predicate.
     */
    public static List<CapturedRow> readManifestosRows(
            final byte[] bytes,
            final List<ExactExportTable.Column> columns,
            final ExportValidationSupport.Budget budget)
            throws IOException, XMLStreamException {
        require(columns != null && columns.size() == 91, "MANIFESTOS_NATIVE_EXACT91");
        final var declared = new ArrayList<>(columns);
        require(declared.size() == 91, "MANIFESTOS_NATIVE_EXACT91");
        final var apiIndices = new HashSet<Integer>();
        final var uiIndices = new HashSet<Integer>();
        for (final var column : declared) {
            require(
                    column != null
                            && column.apiColumn() >= 1
                            && column.apiColumn() <= 91
                            && column.uiColumn() >= 1
                            && column.uiColumn() <= 91
                            && apiIndices.add(column.apiColumn())
                            && uiIndices.add(column.uiColumn()),
                    "MANIFESTOS_NATIVE_COLUMN_BINDING");
        }
        final var workbook = new BoundedExportWorkbook(bytes, budget);
        final String sheet = workbook.worksheet();
        workbook.readStrings();
        workbook.readStyles();
        final List<CapturedRow> rows = new ArrayList<>();
        workbook.capturedRows =
                row -> {
                    require(rows.size() < 12000, "MANIFESTOS_NATIVE_ROW_BOUND");
                    for (int column = 0; column < 91; column++) {
                        budget.cell();
                    }
                    rows.add(row);
                };
        workbook.rows(sheet, declared, false, null, null, null, null);
        return List.copyOf(rows);
    }

    private XMLStreamReader xml(final String name) throws XMLStreamException {
        final byte[] content = parts.get(name);
        require(content != null, "EXPORT_XLSX_PART_MISSING");
        final var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver(
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("EXPORT_XML_EXTERNAL_REFUSED");
                });
        return factory.createXMLStreamReader(new ByteArrayInputStream(content));
    }

    private boolean start(final XMLStreamReader reader, final String name) {
        return reader.isStartElement()
                && NS.equals(reader.getNamespaceURI())
                && name.equals(reader.getLocalName());
    }

    private void next(final XMLStreamReader reader) throws XMLStreamException {
        budget.check();
        require(reader.next() != XMLStreamConstants.DTD, "EXPORT_XML_DTD_REFUSED");
    }

    private String worksheet() throws XMLStreamException {
        String relationship = null;
        final var reader = xml("xl/workbook.xml");
        try {
            while (reader.hasNext()) {
                next(reader);
                if (start(reader, "workbookPr")) {
                    final String value = reader.getAttributeValue(null, "date1904");
                    require(
                            value == null || List.of("0", "1", "true", "false").contains(value),
                            "EXPORT_DATE_SYSTEM");
                    epoch =
                            List.of("1", "true").contains(value == null ? "0" : value)
                                    ? "1904"
                                    : "1900";
                }
                if (start(reader, "sheet")) {
                    require(relationship == null, "EXPORT_ONE_SHEET_REQUIRED");
                    relationship =
                            reader.getAttributeValue(
                                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                                    "id");
                    require(relationship != null, "EXPORT_SHEET_RELATIONSHIP");
                }
            }
        } finally {
            reader.close();
        }
        require(relationship != null, "EXPORT_SHEET_REQUIRED");
        String target = null;
        final var relations = xml("xl/_rels/workbook.xml.rels");
        try {
            while (relations.hasNext()) {
                next(relations);
                if (relations.isStartElement() && "Relationship".equals(relations.getLocalName())) {
                    require(
                            !"External".equals(relations.getAttributeValue(null, "TargetMode")),
                            "EXPORT_EXTERNAL_RELATIONSHIP");
                    if (relationship.equals(relations.getAttributeValue(null, "Id"))) {
                        require(target == null, "EXPORT_DUPLICATE_RELATIONSHIP");
                        target = relations.getAttributeValue(null, "Target");
                    }
                }
            }
        } finally {
            relations.close();
        }
        require(
                target != null && target.matches("/?(?:xl/)?worksheets/[A-Za-z0-9_-]+\\.xml"),
                "EXPORT_WORKSHEET_TARGET");
        final String normalized = target.startsWith("/") ? target.substring(1) : target;
        return normalized.startsWith("xl/") ? normalized : "xl/" + normalized;
    }

    private String richText(final XMLStreamReader reader, final String end)
            throws XMLStreamException {
        final var value = new StringBuilder();
        while (reader.hasNext()) {
            next(reader);
            if (start(reader, "t")) {
                value.append(reader.getElementText());
                require(value.length() <= 32768, "EXPORT_TEXT_BOUND");
            } else if (reader.isStartElement()) {
                require(
                        start(reader, "r")
                                || start(reader, "rPr")
                                || List.of(
                                                "b",
                                                "i",
                                                "u",
                                                "color",
                                                "sz",
                                                "rFont",
                                                "family",
                                                "scheme",
                                                "vertAlign")
                                        .contains(reader.getLocalName()),
                        "EXPORT_OPAQUE_STRING");
            } else if (reader.isEndElement() && end.equals(reader.getLocalName())) {
                return value.toString();
            }
        }
        throw new IllegalArgumentException("EXPORT_STRING_INCOMPLETE");
    }

    private void readStrings() throws XMLStreamException {
        if (!parts.containsKey("xl/sharedStrings.xml")) {
            return;
        }
        final var reader = xml("xl/sharedStrings.xml");
        try {
            while (reader.hasNext()) {
                next(reader);
                if (start(reader, "si")) {
                    require(strings.size() < 600000, "EXPORT_STRING_COUNT_BOUND");
                    strings.add(richText(reader, "si"));
                }
            }
        } finally {
            reader.close();
        }
    }

    private void readStyles() throws XMLStreamException {
        if (!parts.containsKey("xl/styles.xml")) {
            styles.add("builtin:0");
            return;
        }
        final Map<String, String> formats = new HashMap<>();
        boolean cellXfs = false;
        final var reader = xml("xl/styles.xml");
        try {
            while (reader.hasNext()) {
                next(reader);
                if (start(reader, "numFmt")) {
                    final String id = reader.getAttributeValue(null, "numFmtId");
                    final String format = reader.getAttributeValue(null, "formatCode");
                    require(
                            id != null
                                    && format != null
                                    && format.length() <= 1024
                                    && formats.putIfAbsent(id, format) == null,
                            "EXPORT_FORMAT");
                } else if (start(reader, "cellXfs")) {
                    cellXfs = true;
                } else if (reader.isEndElement() && "cellXfs".equals(reader.getLocalName())) {
                    cellXfs = false;
                } else if (cellXfs && start(reader, "xf")) {
                    require(styles.size() < 512, "EXPORT_STYLE_BOUND");
                    final String id = reader.getAttributeValue(null, "numFmtId");
                    require(id != null && id.matches("[0-9]{1,5}"), "EXPORT_STYLE_FORMAT");
                    styles.add(
                            formats.getOrDefault(id, "builtin:" + id)
                                    + ":apply="
                                    + reader.getAttributeValue(null, "applyNumberFormat")
                                    + ":base="
                                    + reader.getAttributeValue(null, "xfId")
                                    + ":fmtId="
                                    + id);
                }
            }
        } finally {
            reader.close();
        }
        require(!styles.isEmpty(), "EXPORT_STYLES_REQUIRED");
    }

    private ExactExportTable.Atom cell(final XMLStreamReader reader) throws XMLStreamException {
        final String type = reader.getAttributeValue(null, "t");
        final String style = reader.getAttributeValue(null, "s");
        final int styleIndex = style == null ? 0 : Integer.parseInt(style);
        require(styleIndex >= 0 && styleIndex < styles.size(), "EXPORT_CELL_STYLE");
        String value = null;
        while (reader.hasNext()) {
            next(reader);
            if (start(reader, "v")) {
                require(value == null, "EXPORT_DUPLICATE_CELL_VALUE");
                value = reader.getElementText();
                require(value.length() <= 32768, "EXPORT_CELL_VALUE_BOUND");
            } else if (start(reader, "is")) {
                require(value == null && "inlineStr".equals(type), "EXPORT_INLINE_STRING_TYPE");
                value = richText(reader, "is");
            } else if (reader.isStartElement()) {
                throw new IllegalArgumentException("EXPORT_FORMULA_OR_OPAQUE_CELL");
            } else if (reader.isEndElement() && "c".equals(reader.getLocalName())) {
                break;
            }
        }
        final String carrier = "XLSX:" + epoch + ":" + styles.get(styleIndex);
        if (value == null) {
            require(type == null || "n".equals(type), "EXPORT_TYPED_NON_NUMERIC_BLANK_UNQUALIFIED");
            return new ExactExportTable.Atom("BLANK", "", carrier);
        }
        if ("inlineStr".equals(type) || "str".equals(type)) {
            return new ExactExportTable.Atom("TEXT", value);
        }
        if ("s".equals(type)) {
            final int index = Integer.parseInt(value);
            require(index >= 0 && index < strings.size(), "EXPORT_SHARED_STRING_INDEX");
            return new ExactExportTable.Atom("TEXT", strings.get(index));
        }
        if ("b".equals(type)) {
            require(List.of("0", "1").contains(value), "EXPORT_BOOLEAN");
            return new ExactExportTable.Atom("BOOLEAN", "1".equals(value) ? "true" : "false");
        }
        require(type == null || "n".equals(type), "EXPORT_CELL_TYPE_UNSUPPORTED");
        return new ExactExportTable.Atom("NUMBER", value, carrier);
    }

    private void rows(
            final String sheet,
            final List<ExactExportTable.Column> columns,
            final boolean api,
            final ExactExportTable sink,
            final LocalDate periodStart,
            final LocalDate periodEnd,
            final java.util.function.Consumer<NativeRow> nativeRows)
            throws XMLStreamException {
        final var reader = xml(sheet);
        int expectedRow = 1;
        Map<Integer, ExactExportTable.Atom> cells = null;
        int width = 0;
        try {
            while (reader.hasNext()) {
                next(reader);
                if (start(reader, "row")) {
                    require(
                            cells == null
                                    && Integer.toString(expectedRow)
                                            .equals(reader.getAttributeValue(null, "r")),
                            "EXPORT_CONTIGUOUS_ROWS");
                    cells = new HashMap<>();
                } else if (start(reader, "c")) {
                    require(++physicalCells <= 650000, "EXPORT_PHYSICAL_CELL_BOUND");
                    require(cells != null, "EXPORT_CELL_OUTSIDE_ROW");
                    final String coordinate = reader.getAttributeValue(null, "r");
                    require(
                            coordinate != null && coordinate.matches("[A-Z]{1,2}[1-9][0-9]{0,4}"),
                            "EXPORT_CELL_COORDINATE");
                    final String letters = coordinate.replaceAll("[0-9]", "");
                    require(
                            coordinate
                                    .substring(letters.length())
                                    .equals(Integer.toString(expectedRow)),
                            "EXPORT_CELL_ROW_COORDINATE");
                    int index = 0;
                    for (int i = 0; i < letters.length(); i++) {
                        index = index * 26 + letters.charAt(i) - 'A' + 1;
                    }
                    require(index <= 256 && !cells.containsKey(index), "EXPORT_CELL_COLUMN");
                    require(expectedRow == 1 || index <= width, "EXPORT_CELL_WITHOUT_HEADER");
                    cells.put(index, cell(reader));
                } else if (cells != null && reader.isStartElement()) {
                    throw new IllegalArgumentException("EXPORT_OPAQUE_ROW_CHILD");
                } else if (reader.isEndElement() && "row".equals(reader.getLocalName())) {
                    require(cells != null, "EXPORT_ROW_STRUCTURE");
                    if (expectedRow == 1) {
                        width = cells.size();
                        require(
                                width > 0
                                        && cells.keySet().stream()
                                                        .mapToInt(Integer::intValue)
                                                        .max()
                                                        .orElse(0)
                                                == width,
                                "EXPORT_CONTIGUOUS_HEADERS");
                        require(api || columns.size() == width, "EXPORT_ALL_UI_COLUMNS_REQUIRED");
                        for (final var column : columns) {
                            final var header =
                                    cells.get(api ? column.apiColumn() : column.uiColumn());
                            require(
                                    header != null
                                            && "TEXT".equals(header.type())
                                            && column.name().equals(header.value()),
                                    "EXPORT_HEADER_BINDING");
                        }
                        if (capturedRows != null) {
                            final Map<Integer, String> headers = new HashMap<>();
                            cells.forEach(
                                    (column, atom) -> {
                                        require(
                                                "TEXT".equals(atom.type()),
                                                "DASHBOARD_CAPTURE_LITERAL_HEADER");
                                        headers.put(column, atom.value());
                                    });
                            capturedHeaders = Map.copyOf(headers);
                        }
                    } else {
                        if (periodStart != null) {
                            final var scope =
                                    columns.stream()
                                            .filter(column -> column.uiColumn() == 36)
                                            .findFirst()
                                            .orElseThrow();
                            final var date = cells.get(api ? scope.apiColumn() : scope.uiColumn());
                            require(
                                    date != null && "NUMBER".equals(date.type()),
                                    "EXPORT_SERVICE_DATE_REQUIRED");
                            final var serial = new BigDecimal(date.value());
                            require(
                                    serial.signum() >= 0
                                            && serial.compareTo(new BigDecimal("2958466")) < 0,
                                    "EXPORT_SERVICE_DATE_SERIAL_BOUND");
                            final BigInteger day = serial.toBigInteger();
                            require(
                                    ExactBinary64.units(serial).shiftRight(1074).equals(day),
                                    "EXPORT_SERVICE_DATE_CARRIER_DAY_AMBIGUOUS");
                            final long serialDay = day.longValueExact();
                            require(
                                    !"1900".equals(epoch) || serialDay != 60,
                                    "EXPORT_PHANTOM_DATE");
                            final LocalDate civil =
                                    "1904".equals(epoch)
                                            ? LocalDate.of(1904, 1, 1).plusDays(serialDay)
                                            : LocalDate.of(1899, 12, serialDay < 60 ? 31 : 30)
                                                    .plusDays(serialDay);
                            require(
                                    !civil.isBefore(periodStart) && !civil.isAfter(periodEnd),
                                    "EXPORT_ROW_OUTSIDE_PERIOD");
                        }
                        final List<ExactExportTable.Atom> row = new ArrayList<>();
                        long mask = 0;
                        int ordinal = 0;
                        for (final var column : columns) {
                            final int nativeColumn = api ? column.apiColumn() : column.uiColumn();
                            row.add(
                                    cells.getOrDefault(
                                            nativeColumn, new ExactExportTable.Atom("ABSENT", "")));
                            if (cells.containsKey(nativeColumn) && ordinal < 63) {
                                mask |= 1L << ordinal;
                            }
                            ordinal++;
                        }
                        if (sink != null) {
                            sink.add(row);
                        } else if (nativeRows != null) {
                            for (int index = 0; index < row.size(); index++) {
                                budget.cell();
                            }
                            nativeRows.accept(new NativeRow(expectedRow, List.copyOf(row), mask));
                        }
                        if (capturedRows != null) {
                            capturedRows.accept(
                                    new CapturedRow(
                                            sheet,
                                            expectedRow,
                                            epoch,
                                            capturedHeaders,
                                            Map.copyOf(cells)));
                        }
                    }
                    expectedRow++;
                    cells = null;
                }
            }
            require(expectedRow >= 2 && cells == null, "EXPORT_HEADER_AND_EOF_REQUIRED");
        } finally {
            reader.close();
        }
    }
}
