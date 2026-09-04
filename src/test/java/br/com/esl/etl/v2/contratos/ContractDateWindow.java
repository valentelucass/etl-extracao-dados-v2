package br.com.esl.etl.v2.contratos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Janela de negócio fechada, reutilizada pelas consultas Data Export e GraphQL de contrato. */
public record ContractDateWindow(LocalDate startInclusive, LocalDate endInclusive) {

    private static final String RANGE_SEPARATOR = "..";

    public ContractDateWindow {
        Objects.requireNonNull(startInclusive, "A data inicial é obrigatória.");
        Objects.requireNonNull(endInclusive, "A data final é obrigatória.");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException(
                    "A data final não pode ser anterior à data inicial.");
        }
    }

    public String asGraphQlInterval() {
        return startInclusive + " - " + endInclusive;
    }

    public String asGraphQlSingleDate() {
        if (!startInclusive.equals(endInclusive)) {
            throw new IllegalStateException(
                    "A consulta GraphQL de Coletas exige uma única data de solicitação.");
        }
        return startInclusive.toString();
    }

    public List<ContractDateWindow> asDailyWindows() {
        final List<ContractDateWindow> days = new ArrayList<>();
        LocalDate current = startInclusive;
        while (!current.isAfter(endInclusive)) {
            days.add(new ContractDateWindow(current, current));
            current = current.plusDays(1);
        }
        return List.copyOf(days);
    }

    /**
     * Divide uma janela em blocos inclusivos, preservando o limite máximo de dias do contrato V1.
     */
    public List<ContractDateWindow> asWindowsOfAtMostDays(final int maximumDays) {
        if (maximumDays < 1) {
            throw new IllegalArgumentException("O máximo de dias por janela deve ser positivo.");
        }
        final List<ContractDateWindow> windows = new ArrayList<>();
        LocalDate current = startInclusive;
        while (!current.isAfter(endInclusive)) {
            final LocalDate maximumEnd = current.plusDays(maximumDays - 1L);
            final LocalDate currentEnd =
                    maximumEnd.isAfter(endInclusive) ? endInclusive : maximumEnd;
            windows.add(new ContractDateWindow(current, currentEnd));
            current = currentEnd.plusDays(1);
        }
        return List.copyOf(windows);
    }

    public static ContractDateWindow parse(final String value, final String configurationKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "A janela de negócio é obrigatória: " + configurationKey + ".");
        }
        final String[] bounds = value.trim().split("\\.\\.", -1);
        if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank()) {
            throw new IllegalArgumentException(
                    "A janela de negócio é inválida: " + configurationKey + ".");
        }
        try {
            return new ContractDateWindow(
                    LocalDate.parse(bounds[0].trim()), LocalDate.parse(bounds[1].trim()));
        } catch (final DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "A janela de negócio é inválida: " + configurationKey + ".");
        }
    }

    public static String rangeSeparator() {
        return RANGE_SEPARATOR;
    }
}
