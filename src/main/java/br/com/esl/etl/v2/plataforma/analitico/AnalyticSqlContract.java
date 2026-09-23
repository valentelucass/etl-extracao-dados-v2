package br.com.esl.etl.v2.plataforma.analitico;

/** Closed public query catalog. Order uses technical identity, never a display label. */
public enum AnalyticSqlContract {
    SQL_01(42, true, "source_key,component_id"),
    SQL_02(123, true, "source_key"),
    SQL_03(41, true, "source_key"),
    SQL_04(13, true, "source_key"),
    SQL_05(54, true, "source_key"),
    SQL_06(31, true, "source_key,component_id"),
    SQL_07(29, true, "source_key"),
    SQL_08(105, true, "source_key"),
    SQL_09(106, true, "source_key"),
    SQL_10(9, false, "entity,provenance,event_id"),
    SQL_11(34, true, "source_key,component_id"),
    SQL_12(34, true, "source_key,component_id"),
    SQL_13(37, false, "trip_key,stop_key"),
    SQL_14(2, true, "entity_key,valid_from,reference_release_id"),
    SQL_15(1, true, "entity_key,valid_from,reference_release_id"),
    SQL_16(4, true, "entity_key,valid_from,reference_release_id"),
    SQL_17(2, true, "entity_key,valid_from,reference_release_id"),
    SQL_18(3, true, "entity_key,valid_from,reference_release_id"),
    SQL_19(3, false, "usuario_id");

    private final int columns;
    private final boolean revisionSelected;
    private final String technicalOrder;

    AnalyticSqlContract(
            final int columns, final boolean revisionSelected, final String technicalOrder) {
        this.columns = columns;
        this.revisionSelected = revisionSelected;
        this.technicalOrder = technicalOrder;
    }

    public String id() {
        return name().replace('_', '-');
    }

    public String localName() {
        return "pub.analytic_lab_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    public int columns() {
        return columns;
    }

    public boolean revisionSelected() {
        return revisionSelected;
    }

    public String technicalOrder() {
        return technicalOrder;
    }
}
