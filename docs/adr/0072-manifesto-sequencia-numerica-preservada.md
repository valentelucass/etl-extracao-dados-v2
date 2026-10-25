# ADR 0072 — Sequência numérica observada em Manifestos

24/10/2026. RG-MAN-019. Responsável técnico Root; owner de negócio não declarado.
Fonte:duas leituras terminais de6399/dia17/10,315 linhas cada,iguais em valores e
multiplicidades;295 recusas INVALID_TEXT_FIELD_TYPE por passagem. Perfil de tipo
mostrou mft_s_n_sequence_code NUMBER em295 linhas e NULL nas20 restantes.

O mapper aceita INTEGER exclusivamente nesse path, além do STRING já permitido.
Conserva o número como número no payload e em rootFields.canonicalJson;não usa
a sequência como identidade,relacionamento confirmado ou texto inferido. Mantém
o limite físico de50 unidades da representação inteira,recusa decimal/boolean/
container e preserva as demais regras textuais. A validação SQL de texto desse
path existente aplica limite quando o token é string;nenhuma migration é alterada.

Exemplo sintético:mft_s_n_sequence_code=123 permanece token123,com raiz sequence_code
independente;1.5 e true continuam recusados. Teste
mapsObservedSequenceNumberWithoutTextCoercion tem RED causal confirmado. GREEN,
pacote e repetição real são registrados em STATES;nada aqui aprova publicação,
semântica global,cutover,commit retido ou efeito SQL.

Recuperação:reverter somente mapper/teste após comparar edições posteriores.
