package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Physical occurrence only; existing mapper results remain immutable and unbound. */
public record ObservedExpansionProjectedRecord(
        int pageOrdinal,
        ExpansionCaptured<? extends ExpansionObservation> captured,
        List<ObservedExpansionProjectedField> fields) {
    public static final int MAXIMUM_FIELDS = 53;
    public static final long MAXIMUM_OCCURRENCE = 2000;
    public static final int MAXIMUM_PAGE_ORDINAL = 1000;

    public ObservedExpansionProjectedRecord {
        Objects.requireNonNull(captured);
        Objects.requireNonNull(fields);
        final int expectedFieldCount = expectedFieldCount(captured.observation());
        // Inspect size and scalar bounds before touching elements, copying, or streaming input.
        final int fieldCount = fields.size();
        if (fieldCount != expectedFieldCount
                || fieldCount > MAXIMUM_FIELDS
                || pageOrdinal < 1
                || pageOrdinal > MAXIMUM_PAGE_ORDINAL
                || captured.occurrence() < 1
                || captured.occurrence() > MAXIMUM_OCCURRENCE
                || captured.binding() != null) {
            throw new IllegalArgumentException("OBS_CAPTURE_RECORD");
        }
        final var boundedCopy = new ArrayList<ObservedExpansionProjectedField>(fieldCount);
        final var paths = new HashSet<String>();
        for (int index = 0; index < fieldCount; index++) {
            final var field = Objects.requireNonNull(fields.get(index));
            if (!paths.add(field.path())) {
                throw new IllegalArgumentException("OBS_CAPTURE_DUPLICATE_FIELD_PATH");
            }
            boundedCopy.add(field);
        }
        // Copy only our bounded collection, never caller-controlled toArray/iterator
        // implementations.
        fields = List.copyOf(boundedCopy);
    }

    private static int expectedFieldCount(final ExpansionObservation observation) {
        if (observation instanceof ContaPagarObservation) {
            return 28;
        }
        if (observation instanceof FaturaClienteObservation) {
            return 53;
        }
        if (observation instanceof InventarioObservation) {
            return 26;
        }
        if (observation instanceof SinistroObservation) {
            return 44;
        }
        throw new IllegalArgumentException("OBS_CAPTURE_OBSERVATION_TYPE");
    }

    public long occurrence() {
        return captured.occurrence();
    }

    public String bindingStatus() {
        return "UNBOUND";
    }

    public boolean mapperValid() {
        return captured.observation().valid();
    }

    @Override
    public String toString() {
        return "ObservedExpansionProjectedRecord[occurrence="
                + occurrence()
                + ",pageOrdinal="
                + pageOrdinal
                + ",binding=UNBOUND,fields="
                + fields.size()
                + "]";
    }
}
