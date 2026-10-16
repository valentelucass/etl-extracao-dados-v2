# Runtime compartilhado — ESL para shadow local

O V2 usa o SQL Server existente e o provedor administrativo externo compartilhado para a prova local de testes retidos. A referência operacional é `../.runtime/sql-server/README.md`; as condições específicas V2 são [ADR0060](../adr/0060-carga-retida-de-testes-com-pin-local-isolado.md) e [STATES](../../STATES.md). A CLI DEV sintética não realiza essa extração.

## Conferência sem efeitos

```powershell
powershell.exe -NoProfile -File scripts/runtime/Test-SharedSqlRuntimeReference.ps1
```

Esse comando confere a configuração pública, os arquivos TLS existentes e o DER E3 aprovado. Verifica somente a presença do arquivo DPAPI administrativo, sem ler, decifrar ou calcular hash da credencial. Não importa o módulo compartilhado, abre conexão, coleta certificado, altera ACL, confiança ou configuração. Resultado `SHARED_RUNTIME_REFERENCE_COMPATIBLE_OFFLINE_ONLY` mantém `physicalReady=false`: o recibo armazenado não demonstra o processo ou a conexão atuais.

Não executar o teste BAT do runtime como substituto da unidade SQL do V2. `Get-ProjetosSqlProfile` pode renovar seus arquivos privados quando a identidade do serviço muda; o perfil V2 E3 exige pin imutável. Qualquer nova folha é recusada antes de autenticar, sem aprendizado automático. Banco permanece único executor de SQL e da leitura real da credencial.

## Sequência operacional

1. Conferir identidade atual do serviço/processo/imagem, versão/hash instalados e TLS do próprio socket. Qualificar os bytes do catálogo e obter admissão Runtime, despacho Root e reserva nova:1conexão/até12SELECT/10s por consulta/deadline60s/sem retry/HTTP/escrita. Registrar os pares das33expressões e o contrato físico estrito.
2. Comprovar a origem/quota aplicáveis, liberação429, limites de fonte e proteção contra escritores conflitantes no shadow. Essas provas são próprias da ocorrência; nenhuma flag histórica ou fixture as substitui.
3. Reservar e executar a pequena captura Coletas: página1/per1/até1000linhas/capHTTP2/30s/10MiB/deadline60s, serial sem redirect/retry/fallback. Orçamento SQL inclui staging, readback e decisão exclusiva do owner. Interromper no primeiro não2xx/429/limite inválido.
4. Confirmar commit por uma leitura independente com reserva própria. Captura parcial retida tem término honesto CANCELLED/limite de teste e recibo `CAPTURE_RETAINED_PARTIAL`; não comprova travessia, DQ ou promoção core/pub. Resultado incerto exige reconciliação, sem repetição/purge/compensação automáticos.

As11entidades exigem composição e prova reais próprias. Não alterar SQL16.0.1000.6, contas, identidades, permissões, serviço/listeners/certificados/trust global/schema/produção para liberar essa sequência. Diagnósticos e recibos novos ficam na pasta central do projeto em `Armazenamento-Projetos`, conforme `../AGENTS.md`; históricos permanecem preservados.

## Unidade08/10/2026

[Checkpoint0477](../continuidade/checkpoints/0477-integracao-runtime-compartilhado-esl-shadow-em-execucao.md). Unidade `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/execucao-esl-shadow-20261008-164952/`. Referência existente e8casos nativos de controle/recusa PASS,9processos; nenhuma credencial resolvida. A coleta OS Banco recusou a identidade do processo; uma coleta nativa independente Root comprovou `OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION)` recusado com erro5 no token atual. Não caracteriza falha SQL/TLS/senha e não libera autenticação. A primeira recusa nativa genérica e o classificador causal corrigido com4casos locais foram preservados. Não repetir coleta idêntica ou alterar permissões por conveniência.

O diagnóstico `BANK_INSTALLED_RUNTIME_READ_ONLY02_V1` é separado: prova limitada ao arquivo instalado/serviço/PID/listeners e à sessão JDBC própria, com TLS/peer antes da autenticação e identidade/versão SQL PREPOST. Declara explicitamente imagem carregada não comprovada e não admite captura/P08/writers. Banco qualificou57casos offline; a composição e o executor completos03 estão em revisão. Nenhum comando físico público está liberado por esse resultado.

No protocolo03 em preparo, Root autoriza uma única unidade dos bytes admitidos numa janela absoluta até15min; Runtime admite os bytes offline; Banco cria sua reserva/claim próprio antes de qualquer efeito. A execução tem até60s e termina antes da expiração Root, sem retry. Não copiar artefatos de fixture nem gerar arquivo Root/Runtime por outro owner. Confirmação de retenção e captura continuam unidades independentes. O launcher sucessor também recusa overlays que encobrem classes públicas atuais com bytes diferentes; os overlays antigos ficam preservados como história.
