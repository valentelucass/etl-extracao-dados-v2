# Checkpoint 0027 — fechamento da documentação posterior ao B58

Data: 2026-09-09. Anterior: 0026-bloco58-fechamento-local.md,
SHA-256 f677cbb981818a247aa247a0b99952087a2b1baed34a8dfe3861075ba27cfa41.
Objetivo do usuário: concluir as pendências atuais para prosseguir. Estado:
EM_EXECUCAO, exclusivamente sucessão documental; não inicia Bloco 59.

Autorização atual abrange reconciliar os cinco anexos já registrados em STATES,
trilha, ponte de continuidade, snapshots, validadores e recibo novo. Zero API,
SQL, campanha física, instalação, credencial, agenda, deploy/cutover, commit/push,
renovação ou consumo de orçamento. Nenhum ledger físico se aplica.

Inventário inicial de 1.521 arquivos e cópias próprias verificados em
target/pos-bloco58-docs/initial-inventory.json e initial/. Somente STATES difere
da fotografia final B58; nenhum arquivo novo existia antes desta manutenção.
STATES B58: 6c81605696f370c686f0e9ccfbd5c5c3ef6226d9b7c8903293d91b0471120e5d.
STATES observado: ee0b5515654740ad49f58116c1dfee37f54039e31dcefe798de8b8df3f68e728.
Ambas as versões permanecem preservadas, sem atribuição de autoria às diferenças.
Recibo B58: 31f3f258e738e34899357148fe2f0d63d56d347d29c790b7bbef7a2be731f087.
Manifest B58: 30cffcb2e2056ca43506101fad17cfb13dc8e4cf7c6b1fcd9c692c5f4b548b31.

Recusa reproduzida no validator original: exit 1, B58_HASH_STATES.md. Log e
resultado preservados em initial-gate.log/initial-gate.json no diretório próprio.
Isso é drift documental após o fechamento, não regressão do Java. Nenhum PASS
atual é presumido; Java 1.303/zero falhas/erros/cinco skips segue prova histórica.

Decisão: quatro deltas exatos (STATES, trilha, RETOMADA e Test-Bloco58Local),
com predecessor B58 intacto e snapshots em docs/continuidade/historico/pos-bloco58.
O novo validator verificará revisões atuais; o B58 continuará lendo sua fotografia
e propagará somente os hashes sucessores exatos. Nenhuma exceção por diretório.

Próximas ações:
1. Atualizar STATES → trilha e criar manifest/validator/guards da sucessão.
2. Conferir cadeia privada, guards, scanner e UTF-8; não repetir Java inalterado.
3. Salvar checkpoint 0028, último delta, diff/reverse --check sem aplicar e recibo.

Nenhum efeito externo desconhecido ou processo físico ativo. 67/115, 48 pendentes,
191 rotas abertas, zero AGORA; nenhum aceite novo. Oráculos, V2-041 e demais gates
externos mantêm suas dependências. Recuperação compara apenas deltas próprios
contra initial, preservando os cinco anexos e qualquer edição posterior.
