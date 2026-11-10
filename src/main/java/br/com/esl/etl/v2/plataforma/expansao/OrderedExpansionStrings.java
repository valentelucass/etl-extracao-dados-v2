package br.com.esl.etl.v2.plataforma.expansao;

import java.util.List;

/** Immutable ordered string values. Each concrete field type retains its own finite bounds. */
public interface OrderedExpansionStrings {
    List<String> items();
}
