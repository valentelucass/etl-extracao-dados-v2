package br.com.esl.etl.v2.plataforma;

/** Canonicalização textual equivalente a {@code LTRIM/RTRIM} usada pelos contratos SQL V2. */
public final class SqlText {

    private SqlText() {}

    /** Remove exclusivamente espaços ASCII U+0020 das extremidades. */
    public static String trimAsciiSpace(final String value) {
        int first = 0;
        int last = value.length();
        while (first < last && value.charAt(first) == ' ') {
            first++;
        }
        while (last > first && value.charAt(last - 1) == ' ') {
            last--;
        }
        return value.substring(first, last);
    }
}
