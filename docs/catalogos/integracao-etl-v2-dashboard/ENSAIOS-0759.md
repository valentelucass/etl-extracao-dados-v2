# Ensaios0759 — extração atual, período escolhido e armazenamento físico

**Ensaio concluído para recebimento de Faturas4924.** Dois ciclos reais aprovados, com60min08s entre fim do primeiro e início do segundo. Ler sempre este arquivo, ADR0079, runbook e recibos do chat. Produção completa das demais entidades e publicação de negócio ainda não liberadas.

Instalados: pacote SQL de recebimento isolado, entrada produtiva opt-in reutilizando o capturador qualificado Faturas4924, launcher com credenciais por stdin e limite de memória compartilhado. Campanha externa limitada a duas execuções reais com60min depois do término, concluída e desabilitada em09/11. A qualificação se restringe a esta entidade/recebimento.

Provas concluídas até01h02 de09/11: compilação899fontes/843testes, estilo/Enforcer e29casos de escopo aprovados. Duas verificações arquiteturais gerais continuam falhando por28APIs e1registro anteriores; introspecção real antes/depois de excluir apenas as3classes novas confirmou exatamente as mesmas violações. Não declarar suíte geral de arquitetura aprovada.

`receiving-range0759-attempt3.json`: extração ATUAL de30/10 a01/11, três partições inclusivas:1179/115/0ocorrências, total1294,18HTTP, terminal real em todas.52campos/mapper qualificados; todos os envelopes/ordinais/SHA256UTF16 iguais após leitura JDBC de volta. Dia vazio tem INFO e página terminal reais. Não houve cópia do V1 nem reapresentação de captura histórica.

`receiving-real-failure0759.json`:8casos físicos com envelopes reais, sem novos HTTP. Contagem incorreta51905, data incoerente51906, reentrada deFAILED/STORED51904, INSERTdireto229, timeout1222 após inserção ao bloquear apenas a linha de cobertura no banco novo, leitura independente0linhas/progresso anterior intacto,29/10continua descoberto. As únicas linhas deliberadamente retidas dos testes de falha são metadadosFAILED; nenhum payload de negócio parcial foi comprometido.

Falhas anteriores preservadas: tentativa1parou antes deHTTP por ausência de separador no endereço JDBC (afetava o nome do certificado); diagnóstico real isolado corrigiu apenas o separador e passou, sem bypassTLS. Tentativa2capturou1179originais válidos mas o adaptador SQL procurava `data.service_at` em vez do envelope qualificado `record.service_at`;0linhas de negócio gravadas. Correção SQL002 separada aplicada e registrada,001original intocado. Depois disso a tentativa3passou de ponta a ponta. JAR/manifesto anteriores preservados; runtime corrente é rev2, JDBC13.4privado, defaultShadow12.8intacto.

Task Scheduler `ETL-V2-Receiving-Qualification-0759` registrado com usuário Windows existente/Interactive/Limited, IgnoreNew e prazo45min. Primeiro disparo observado Running às01h01; inclui29/10anterior à janela normal e02–09/11. Segundo disparo só é marcado depois de concluir o primeiro. Não há loop interno/daemon, alteração do V1 ou liberação de execução indefinida. Recibos em outputs/hourly0759 do chat. A prova de recorrência só fecha com os DOIS resultados e horários medidos.

## Primeiro ciclo e conferências independentes

`hourly0759/hourly-cycle1.json` aprovado:01h00m21–01h06m05,4862ocorrências/9datas.29/10recuperado com1008ocorrências, antes dos oito dias02–09/11. Cada envelope de origem/ordinal/hash voltou idêntico do SQL. Próximo disparo definido para02h06m06, exatamente60min depois de concluir o worker; não antecipar para simular teste.

`hourly0759/live-overlap-refusal.json`: durante o ciclo ativo, outro processo real não adquiriu o mutex de origem nem o applock SQL(resultado-1). ZeroHTTP/escritas de negócio nesse teste. O primeiro ciclo usou processo one-shot protegido com pico de850403328bytes/2GiB eexit0; não criou daemon.

`post-cycle1-readonly0759.json`:12tentativasSTORED,3FAILEDdeclaradas,0RUNNING;6156ocorrências físicas,12datas29/10–09/11,23747404bytes de envelopesUTF16.26sinônimos continuam na rota original, atributos dos bancos/logins antigos e versão preservados. Soma inclui1294do intervalo escolhido e4862do ciclo, sem contar o payload da tentativa recusada como recebido.

`storage-profile0759.json`:6156envelopes com0divergência de hash/data/identificação do envelope;52campos em todas as ocorrências,320112células de origem; ordinais íntegros em12partições, perfil de tipos/tamanhos sem valores de negócio. Isso verifica integridade de recebimento, não paridade semântica atual de todas as telas.

`jdbc-real-dashboard-v1-v2-0759-attempt1.json`: Java17/JDBC13.4real com identidades respectivas;26objetos/958nomes/26rotasV1 preservados; leitor do dashboard vê12passos de instalação e o status público de12recebimentos/6156linhas no banco novo. Identidade ETL continua sem administração/acesso aos bancos antigos. Não houve boot/deploy/troca do portal.

`receiving-integrity-summary0759.json`: DBCC CHECKDB físico/lógico sem violações,3tabelas novas aprovadas no DBCC de regras; avaliação direta de todos os163CHECKs e119FKs sem violação, sem desabilitar/alterar regras ou collation. Comando geral DBCC CHECKCONSTRAINTS registrouSQL446; resultado NÃO foi declarado aprovado. Primeira sonda desse comando também usou duas opções mutuamente exclusivas, depois corrigidas conforme a [sintaxe Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/database-console-commands/dbcc-checkconstraints-transact-sql?view=sql-server-ver16). A verificação direta evita a construção das expressões de apresentação desse diagnóstico; o erro446permanece preservado, sem inferir corrupção. Primeiro auxiliar FK teve agrupamento de hashtables diferente noPS5 e produziu207; corrigido para objetos/agrupamento explícitos, repetindo apenas FKs,119aprovadas. Falhas e recibos anteriores preservados.

`new-database-backup0759-attempt2.json`: cópia nativa do novo banco pelo serviço SQL na pasta padrão da instância, COPY_ONLY/CHECKSUM/COMPRESSION/NOINIT,5723826bytes comprimidos/40976384originais; backup2310ms,VERIFYONLYcomchecksum377ms. Nenhuma restauração de banco ou mudança de ACL/identidade. A primeira preparação tentou inspecionar ACL da pasta SQL pelo cliente Windows e foi recusada antes de enviar BACKUP; essa leitura local não é necessária para a operação nativa do serviço SQL no diretório próprio, que foi usada no sucessor sem conceder acesso adicional ao cliente. Não copiar/mover a.bakcomo se fosse arquivo público. [COPY_ONLY](https://learn.microsoft.com/en-us/sql/relational-databases/backup-restore/copy-only-backups-sql-server?view=sql-server-ver16) não muda a base diferencial; [VERIFYONLY](https://learn.microsoft.com/en-us/sql/t-sql/statements/restore-statements-verifyonly-transact-sql?view=sql-server-ver16) verifica legibilidade/completude, sem substituir ensaio efetivo de restauração/RTO.

`preservation0759-cycle1.json`:2136arquivos qualificados anteriores,94outputs anteriores e16pins atuais verificados; conteúdo histórico dos documentos preservado após prefixo0759, HEADs/alterações preexistentesV2/dashboard/V1 mantidos, quatro conjuntos de metadados SQL protegidos idênticos. Verificação de ACL herdada do diretório de dados encontrou0AllowparaEveryone/AuthenticatedUsers/Users; nenhuma ACL foi alterada. Patch próprio de15fontes passou gitapplyreversecheck sem aplicação.

## Limites antes de produção permanente

Campanha de qualificação com duas execuções, usando sessão Windows existente Interactive; não prova operação após logoff/reinício do servidor. O intervalo adotado preserva o V1:60minAPÓSconcluir. Primeiro ciclo medido5min44s; a distância entre inícios inclui essa duração. A meta de atraso visível precisa considerar duração, fila e futura publicação.

Recuperação desta rota consulta cobertura durável por entidade/escopo/data, sem saltar datas ausentes. Acima de4datas antigas pendentes, um disparo prioriza até8antigas e adia a janela normal, limitando carga; até4antigas somam-se aos oito dias normais. Isso precisa ser dimensionado com todas as entidades antes de produção permanente. RegistroRUNNINGórfão por morte abrupta, recuperação de resultado incerto após commit, alertas/retencão/backup, histórico inicial/âncora configurável e política de alterações anteriores aD-7 ainda requerem ensaios/política próprios. Não transformar cobertura de captura em prova de atualização de registros antigos já cobertos.

Demais entidades, fonte auxiliar Raster, referências/calendário e core/mart/pub de negócio continuam fora do aceite deste recebimento inicial. Preservar os134contratos/24alertas do mapeamento anterior; nenhuma view de fato foi simulada com valores vazios para liberar o dashboard. A cadência desta entidade foi fechada abaixo.

## Fechamento — segundo ciclo real e estado final

`hourly0759/cadence-proof.json` PASS. Segundo processo novo iniciou02h06m13e concluiu02h10m45de09/11:3854ocorrências, oito datas02–09/11,56HTTPatuais,52campos/mapper/terminal e readback exato em todas. Primeiro ciclo68HTTP/4862ocorrências/9datas. Espera efetiva3608,141532s(60min08,14s); intervalo entre inícios3952,320903s(65min52,32s), incluindo duração do primeiro. Sem relógio abreviado ou SQL apresentado como extração de origem.

Antes do primeiro havia1data antiga descoberta(29/10); antes do segundo0. O segundo leu cobertura durável em processo distinto(PIDs do worker18776e41292) e extraiu novamente os oito dias normais. Tarefa própriaETL-V2-Receiving-Qualification-0759 terminouexit0/Ready, depois foi desabilitada pelo verificador, sem alterar outras tarefas ou criar produção indefinida.

`post-final-readonly0759.json`:20recebimentosSTORED,3FAILEDdeclarados,0RUNNING;10010ocorrências físicas em versões de extração,12datas cobertas,38382828bytesUTF16originais. Não confundir10010ocorrências históricas com entidades únicas. `storage-profile0759-final.json`:52campos em todas,520520células;0divergência de hash/data/envelope, ordinais completos nas20partições.26rotas/atributos/logins/versão preservados. HEADfrontend200/API401autenticação exigida, mesmosPIDs5173=67612e5010=2024; isso não é aceite funcional autenticado da API.

Relatório final e manifesto em outputs/Prova_Extracoes_e_Recebimento_0759.txt e outputs/manifesto-extracoes0759.json do chat. Patch de15fontes próprias revisado sem aplicar;29testes de escopo e ASTlocal55092nós/138734arestas atualizados. Histórico/falhas preservados. Seguir [runbook](../../runbooks/recebimento-produtivo-0759.md) antes de outra execução.

Conclusão delimitada: recebimento real, intervalo escolhido, recuperação de período e cadência desta entidade foram comprovados. Os quatro marcos originais de implantação integral continuam parcialmente concluídos; os limites/políticas acima precisam de implementação e qualificação próprias antes de corte.

Nenhum sinônimo/configuração/deploy do dashboard ou efeito em ETL_SISTEMA/Shadow/DASHBOARDS está incluído. O consumidor atual continua no V1. A camada nova de staging conserva os valores originais, mas ainda não substitui fatos/views de negócio necessários ao dashboard.
