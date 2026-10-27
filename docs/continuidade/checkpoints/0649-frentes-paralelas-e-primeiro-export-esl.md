# 0649 — Frentes paralelas e primeiro arquivo ESL confrontado

27/10/2026. O usuário determinou organizar outros chats para corrigir em
frentes paralelas, informou V1 parado e pediu que o supervisor fique somente
supervisionando. A seção ETL V2 foi criada no aplicativo e recebeu estes chats:

| Papel | Chat |
| --- | --- |
| Supervisor de finalização | 01a17ee4-5f83-7051-9cec-ffc8e16642fe |
| Conferência ESL | 01a17f33-5113-7010-9b68-6b698afede99 |
| Campos e regras | 01a17f32-f63f-7223-a2c4-70b88135ab49 |
| Execução e qualificação | 01a17f32-9681-71c2-90f4-3fe912c68684 |

## Divisão e posse de execução

Supervisor coordena, revisa, integra documentação e publica. Não executa mais
batches pesados ou navegador. Execução e qualificação é o único executor
pesado delegado: esta mudança de dono substitui a designação operacional
anterior de Root único, conforme pedido humano; não altera os controles.
Campos e regras prepara correções/testes, sem executar lotes. Conferência ESL
é o único dono do navegador, exportações e preparo de comparações. Pedidos de
qualificação ficam em arquivos request-*.json da equipe, encaminhados ao executor.

Continuam obrigatórios Original1GiB finito, memória livre>=4096MiB e commit<=85%,
um lote protegido por vez, slot CAS, pins e fechamento conhecido. A transferência
ocorreu com rootActiveExecSessions vazio. Apenas executionOwnerThread,
teamDelegation0649 e updatedUtc foram modificados no ledger; o restante foi
comparado profundamente e preservado. Não rebasear contadores/históricos.

B = C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01.
Briefing B/supervisor-team0649-01/briefing.json, SHA256
8CA18CCBE520721E2492233B1E772ED6B112EA647B38670746EC85B043924A3E.
Recibo de posse no mesmo diretório, delegation-receipt.json, SHA256
84D803D5D7FB1D6B42C7E64F4C04B0696DB044E56FA27B81B8D35ED35E2DD78D.
Backup exato e imagem anterior ao CAS preservados no diretório da equipe.

Isso não libera DDL18SQL/quatrogrupos ou Raster7DDL. Não altera SQL16.0.1000.6,
instalação, contas, segredos, permissões, serviços, listeners, certificados/TLS,
PM2/V1/WMS ou produção. V1 parado é informação do usuário, sem reinício por
suposição. Source/SQL futuros continuam exigindo admissões e unidade próprias;
LatestMainTyped e STOP429 permanecem. Nenhuma autorização nova de efeito
Source/SQL/DDL foi criada pelo recibo de organização.

## Coletas: arquivo externo obtido e diferenças localizadas

Data Export6908: filtro picks.request_date confirmado17/10/2026–17/10/2026,
demais campos visíveis vazios. Exportar gerou arquivo com um registro em
27/10/2026 17:54:01, depois baixado. Não foi usado arquivo antigo de2025.
Planilha original de31 colunas permanece privada em Downloads; nenhuma
alteração de cadastro/template foi salva.

O confronto novo usou o payload_json do readback SQL retido0626, com pin1ABD,
e o arquivo XLSX atual. Um registro e mesmo ID/número comprovados. Cobriu31
campos por mapa explícito; execução v2 terminou0,24 células iguais e7 diferenças.
Esse resultado é diagnóstico, não paridade nem sete defeitos do produto:

- Três vínculos do mapa manual do comparador estavam errados. Definições e
  valores foram investigados: Cliente=pck_cor_nickname; Região da
  Coleta=pck_prn_name; Filial Responsável=pck_prn_drt_nickname. A correção
  precisa de sucessor próprio; o resultado v2 não será sobrescrito.
- created_at e updated_at concordam até o segundo civil, mas a exportação não
  conserva os mesmos subse segundos nem marcador de fuso. A comparação estrita
  marcou diferenças; não arredondar para afirmar fidelidade integral.
- raw status=finished aparece Coletada no XLSX. O catálogo local e o readback
  status_label usam Finalizada. É uma divergência concreta a ser tratada pela
  frente Campos e regras, preservando o código bruto.
- pck_pus_ore_trigger=finish aparece Baixar Entrega/Coleta na exportação.
  occurrence_action SQL=Coleta Realizada é derivado do status; não equiparar
  silenciosamente essa coluna ao campo original da ação da ocorrência.

Prova B/esl-coletas-comparison0649-02-root-control/qualification.json, SHA256
167B4670F3626E032FF3A14A53EE53FE39527A33565926A8DA217DD600706B5A.
Result.json SHA25642FE89A2BCE249E36A945117D7F1E6C7FC6F5B1B9EAF18BD8A9E04E867C36746.
Original0/1.660ms/600.887.296bytes, sem nova API ETL/SQL/DDL nessa análise.
V1 do comparador falhou no acesso coordinate de EmptyCell; corrigida somente
a construção do endereço da célula, preservando controle01 e encerramento.
Os arquivos de entrada foram verificados antes/depois e os bytes do ledger
restaurados. Nenhum valor de negócio foi publicado nos resultados técnicos.

## Continuidade no navegador e nas outras frentes

O usuário autorizou: se uma aba travar, abrir outra e fechar a que apresenta
erro. O fechamento pelo controle de organização do navegador funcionou; não
exigir do usuário encerramento manual por hábito. A sessão permaneceu conectada.
Manifestos ainda não tem exportação completa: a prévia ampla voltou a travar
na confirmação do calendário. URL com filtro sugerido foi ignorada; não iterar
URLs inventadas nem inferir que um filtro no endereço foi aplicado.

Frente ESL recebe os oito arquivos restantes, no recorte exato da matriz0648,
e prepara cruzamento por registro/campo preservando duplicatas. Nove Data
Export e somente Usuários GraphQL continuam separados. A comparação de Coletas
e o inventário de Manifestos não fecham a comparação integral das entidades.

Campos e regras recebe Coletas; Execução recebe as qualificações e o próximo
passo interno de Users/P3 após Progressive13 estático0647: catálogo8objetos,
17constraints57colunas33params, adapter/guard/engine. Não repetir Own59 nem
adicionar escopo por ternário que o faça alias de Localização.

Última Source permanece MainManifestos0645. Coletas/Manifestos/Cotações seguem
com rollback-only, não retenção permanente. Users sem EOF/cursor retomável,
demais bancos, comparação integral e estruturas proibidas permanecem abertos.
O supervisor deve acompanhar progresso real dos três chats e integrar somente
entregas revisadas/qualificadas, mantendo commit e push.
