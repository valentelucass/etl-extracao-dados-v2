package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.ZoneId;

/** Valor tipado que pode ser serializado para o formato temporal da fonte. */
public sealed interface DataExportFilterValue permits BusinessDateRange, SourceDateTimeRange {

    String formatForSource(ZoneId sourceZone);
}
