# Complemento do Bloco 57 — F1/F2/F3

Escopo local adotado pelo usuário em 09/09/2026: fechar as lacunas da auditoria
antes de escolher outro bloco. A fotografia B57 anterior continua preservada;
o complemento não reescreve seus manifests, testes, recibos ou resultados.
Estado corrente e evidência final pertencem ao STATES e ao novo recibo em
`target/bloco57-complemento/final/receipt.json`; não presumir PASS se faltar.

## Localização: três provas diferentes

O consumidor B57 usava `map(ordinal, String)`; o pipeline chama a entrada JsonNode.
O novo teste atravessa os dois mappers e o contrato/streamer/caso de uso local:

| Valor JSON sintético em invoices_volumes | Mapper String | Mapper JsonNode | Pipeline com contrato sintético B55 |
| --- | --- | --- | --- |
| Número 0 ou 1 | Válido com léxico disponível | UNVERIFIED_NUMERIC_WIRE_LEXEME | Recusado no contrato antes do staging |
| String "0" | Válido | Válido | Travessia local válida |
| null | Válido | Válido | Travessia local válida |

O contrato sintético B55 declara STRING para esse campo; a recusa antecede o mapper.
Não houve remoção da proteção lexical, coerção, alteração da decisão LOC-01–07,
transporte de léxico para produção ou alegação de comportamento ESL. A expectativa
de linha válida falha corretamente quando o mapper retorna quarentena.

## Cotações: comando e entrada futura implementados

O executor novo é separado dos runners históricos 6908/6389/4924. Código em
`src/test/.../contratos/mapping`, sem composição positiva do runtime, SQL ou GraphQL.
O consumidor MapperCharacterization continua sem acesso a rede, ambiente ou JDBC;
o executor recebe os bytes e o oráculo e o chama uma página por vez.

Comando de preflight, sem rede nem reserva:

```powershell
pwsh -NoProfile -File scripts/probes/Invoke-CotacoesCharacterization.ps1 -InputPath C:\CAMINHO_PRIVADO\cotacoes-input.json
```

Comando de aquisição futura, somente após inputs/adoção específicos:

```powershell
pwsh -NoProfile -File scripts/probes/Invoke-CotacoesCharacterization.ps1 -InputPath C:\CAMINHO_PRIVADO\cotacoes-input.json -Execute
```

O launcher usa Java 17/heap512, Maven offline e POM equivalente com saída nova em
`target/bloco57-source-command/command-UUID`. Não executa clean, instala dependência
ou usa perfil de fonte histórico. A classe de comando só é selecionada pela
propriedade explícita `bloco57.cotacoes.command=preflight|execute`; verify comum a
deixa desabilitada. `-Execute` inicia o runner depois do preflight interno.
Logs do launcher contêm códigos fixos; recibos nunca contêm respostas ou credencial.

Copiar [a entrada pendente](cotacoes-input.pendente.json) para área privada e
preencher todos os campos. O arquivo público permanece com null/false e é recusado.
`baseUri` deve ser HTTPS sem porta explícita, caminho, query, fragmento ou usuário;
`logicalHost`, `sourceInstance` e `tenantScope` são bindings explícitos, sem sentinelas.
A disponibilidade de um subdomínio não fornece automaticamente os scopes.
As referências de autorização, garantia temporal, representatividade e credencial
devem apontar a evidências adotadas fora deste JSON. Texto ou hash não comprova
autorização humana nem garantia do fornecedor; precisam de conferência prévia.
`notBefore/notAfter` delimitam vigência explícita de no máximo duas horas e nunca
são renovados pelo comando. Nenhuma vigência está preenchida nesta entrega.

`windowStart/windowEndExclusive` são instantes ISO; `civilDateFrom/civilDateThrough`
são datas ISO explicitamente determinadas pela garantia temporal. O executor
não deduz as bordas civis a partir dos instantes. Escolher janela recente, conforme
restrições públicas e adoção; não usar espera/retry para renovar limite histórico.

`oracleFile` é arquivo privado UTF-8 até 65.536 bytes, independente do mapper,
vinculado pelo SHA-256 dos bytes em `oracleSha256`. Estrutura fechada:

```json
{
  "decisionSha256": "SHA256_DA_DECISAO_V2_027",
  "metadata": {"fields": ["EXPECTATIVAS_INDEPENDENTES"], "filters": ["EXPECTATIVAS_INDEPENDENTES"]},
  "pages": [[{"/quarantine": "NONE", "/sequence_code/presence": "VALUE"}]]
}
```

Esse exemplo estrutural está incompleto e não passa no preflight. Cada linha
precisa de presença e wireType dos nove campos da decisão V2-027, motivo de
quarentena esperado e, quando válida, chave tipada e frescor/origem. Outras saídas
tipadas devem ser acrescentadas conforme o oráculo e a pergunta da amostra.
São 1–3 páginas, no máximo três linhas físicas por página, 1–9 linhas totais.
Expectativas são posicionais e independentes; reordenação/divergência interrompe
o caso, sem tentar fabricar match por dedupe. Nenhum dado do oráculo é impresso.
O `/info` precisa coincidir com as expectativas de fields/filters antes dos dados.
Tipos sintéticos ou exemplos públicos não devem ser copiados como garantia real.

A credencial já provisionada usa a variável de processo `B57_COT_BEARER_TOKEN`.
Não há leitura/edição automática de `.env` nem criação/rotação de credencial.
O curl recebe URL e Authorization por stdin; argv contém apenas opções fixas de
GET, HTTPS, caps, timeout, zero retry e zero redirect. Nenhum body GET ou fallback.

Os limites completos estão no [pacote executável](cotacoes-fonte-futura.json).
O cap físico três é conservador: não é inferência de que `per` limita linhas na ESL.
São no máximo quatro requests (um info e três páginas), 65.536 bytes/resposta,
262.144 bytes totais, 120 segundos, requests de até 30 segundos e intervalo mínimo
de dois segundos após cada resposta. Divergência de metadata/mapper, 429, HTTP
não-2xx, timeout, caps ou resultado desconhecido encerram a tentativa sem retry.

O diretório `target/bloco57-source-future` é criado exclusivamente na primeira
execução válida. Se já existe, nova execução é recusada mesmo com outro executionId.
Cada request tem reserva persistida antes do curl e observação separada. Ausência
de observação significa resultado desconhecido. Ler recibos antes de qualquer
decisão posterior; não apagar a pasta, renovar teto nem reutilizar quotas B53–B56.
Payload e metadata brutos ficam somente na memória limitada; persistem status,
contagens, paths técnicos, motivos e hash do artefato de oráculo.

Um resultado BOUNDED_SAMPLE_MATCH comprova só o recorte planejado e as expectativas
comparadas. Não fecha Q-COT-01/V2-012a nem comprova estabilidade global, relações,
SQL, completude, snapshot, sweep, paridade core ou cutover.

## Pergunta documental dirigida adicional

Pergunta: quais restrições de intervalo/idade devem orientar a entrada do executor?
Consultadas apenas as descrições DATA EXPORT, “2) Consulta estrutura dos templates”
e “3) Executa consulta relatório” da coleção já obtida, SHA-256
`2412415bc4387d720c95791bd61f0b8ba6e7ab64f1d482aca6903ffbfc5d23ac`.
O texto indica dois segundos por IP; janelas históricas de 31 dias a seis meses
têm restrição de uma hora, e acima de seis meses, de 12 horas. Não implementar
espera/fallback para essas restrições. GET+template e tenant são documentados;
tipos dos nove campos, fronteiras temporais, scopes e oráculo não são fornecidos.
Não houve novo download, API real, reanálise das 191 entradas ou repetição de sondas.

P08–P11 não receberam evidência nova pertinente. V2-029–032 continuam dependentes
de identidade/grão/crosswalks; FAT-02 exige decisão fiscal e V2-041 prova de rotação.
Documentação pública não é aprovação para operação e estes testes não são prova ESL.

## Preservação e recuperação

STATES observado após B57 e a versão entregue diferiam no ponteiro 0016/0017 e no
registro de checks. As duas versões permanecem no histórico do complemento, com
hashes distintos e sem atribuição de autoria. O novo STATES preserva o texto da
fotografia entregue e acrescenta o resultado atual; checkboxes não foram alterados.
O novo manifesto parte do B57 imutável e confere sete deltas exatos. Validators
antigos leem seus snapshots e propagam apenas esses deltas; não há exceção de pasta.
Os seis guards históricos continuam testando a fotografia antiga; os guards novos
testam a sucessão atual. Recuperação final contra as 1.463 cópias iniciais da rodada,
preservando a edição observada, logs falhos e qualquer modificação posterior.
