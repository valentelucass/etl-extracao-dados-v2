# P11 — correções aplicadas e auditoria pública verde

P11_PUBLIC_FEED_PATCHED. Autorização do usuário: “autorizo, encerre logo”,
em resposta ao pedido de acesso público Maven Central/NVD sem credenciais.

POM atualizado: Jackson 2.17.2 → **2.18.11**; JDBC 12.8.1 → **12.8.2.jre11**;
pin de autenticação nativa **12.8.2.x64**, coerente com o driver e disponível no
Central. A correção JDBC mantém a linha 12.8; o candidato 13.4 anterior não foi
promovido. Nenhuma DLL nativa foi executada, instalada ou qualificada nesta rodada.
Licenças publicadas nos POMs: Jackson Apache 2.0, JDBC MIT; DLL nativa permanece
Microsoft Proprietary License. A DLL não faz parte do scan de JARs realizado.

## Evidência executada

Dependency-Check 12.2.0 atualizou **41.788 registros NVD**, com consulta final em
21/09/2026 às 19:08:25 -03 e lastModified 21:17:22Z. JSON e HTML produzidos,
**13 dependências, zero vulnerabilidade, zero erro de scanner e exit 0**, mantendo
threshold 0.0, failOnError e nenhuma exceção/supressão nova. Inclui dependências
de testes; analisadores de outros ecossistemas e serviços terceiros desativados.
O parser efetivo do repositório aceitou o relatório real: zero exceção efetiva.

**205 testes/29 classes**, zero falhas/erros/skips: JDBC sintético,
fronteiras/arquitetura, mappers, JSON, payloads e paths. Compilação completa,
Enforcer, Spotless e Checkstyle PASS. Testes em cópia com os três pins exatos
promovidos ao POM, sem rede de negócio/SQL e sem integração nativa. Recursos
copiados byte a byte; cópia automática desativada apenas na cópia de testes.
Suíte direcionada: não se alega execução integral de todos os testes ou cobertura.

A validação do relatório revelou um bug real: o resultado vazio de um if
PowerShell virava null, e .Count falhava para dependência sem vulnerabilidades.
A coleção agora é materializada como array. Seis regressões comprovam aceitação
de lista ausente/vazia e recusa de um achado score zero, múltiplos achados, lista
null e vulnerabilidades suprimidas. A política e o catálogo de exceções não mudam.

Recibos e hashes: [resultado.json](resultado.json). A nova sucessão preserva
o manifesto anterior e os snapshots exatos; o validador de preparação confere
o POM histórico da sua fotografia, enquanto a nova revisão confere os pins atuais.
Não foi reescrito manifesto histórico para acomodar as versões.

## Falhas preservadas e limites

Primeiro scan: o guard recusou o pipe interno do seletor NIO do Windows. A
correção limita a permissão ao PipeImpl do JDK, sem abrir listener de aplicação.
O cliente NVD registrou falhas transitórias e recuperou a atualização por seu
retry interno; o recibo final comprova a conclusão completa. Não houve retry
manual de efeito desconhecido. O acesso da JVM ficou limitado a Central/NVD,
com ambiente sem credenciais e settings próprios vazios. TLS usou a confiança
padrão do runtime; nenhum certificado/segredo do projeto foi aberto ou copiado.

Primeira suíte: três erros do harness, nenhum de regressão funcional — recursos
de testes no diretório errado e subprocesso do teste de junction recusado.
Corrigidos o destino test-classes e a permissão exclusiva ao helper pwsh chamado
pelo teste conhecido, sob diretório temporário próprio. Os 205 passaram na
segunda execução; XMLs vermelhos conservados em test-01-reports. O primeiro
parser real e a primeira asserção do harness também foram preservados como RED.

## Estado das P e próxima ação

P11: correções, atualização pública do feed e auditoria técnica concluídas.
Aceite nominal da baseline por Segurança continua ausente; nenhum nome ou
revisão humana foi inventado. Autenticação nativa/SQL exige qualificação própria.
P10/P12/P14/P15/P21 conservam sua preparação e requisitos específicos, detalhados
na [matriz atual](matriz-atual.json); provas B56 e SQL anteriores continuam válidas
no seu escopo. P09 não reavaliado. Contadores **39/45 e 67/115** preservados.

Próximo passo realmente elegível: revisão/aceite nominal desta baseline por
Segurança, ou intake de novo pacote G02/G05/G03/G04 da P correspondente. A
autorização de consulta pública foi consumida nesta tarefa e não autoriza
fonte de negócio, SQL, produção, rotação, deploy, paridade, cutover ou aceite V2.

## Fechamento validado

Sucessão pública: 10 contraprovas PASS; composição da cadeia anterior e trilha
ampla PASS. Preparação histórica: 13 contraprovas, seis P/17 requisitos e
recibos anteriores conferidos. Política/implementação: 33 casos,30 recusas,
zero exceção efetiva. Parser real e seis regressões PASS. AST de cinco scripts
e scan delimitado do catálogo PASS. Recibos em [validacoes.json](validacoes.json).
Oito deltas explícitos sobre 3.722 arquivos; 3.714 permanecem intactos.
As quatro prévias documentais foram conservadas como sufixos byte a byte.
POM alterado somente nos três pins; os arquivos anteriores estão nos snapshots.
