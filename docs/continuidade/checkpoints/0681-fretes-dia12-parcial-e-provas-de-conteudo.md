# 0681 — Fretes: dia12 retornou um registro; página seguinte falhou na ESL

A nova unidade independente de12/10/2026 retornou INFO200 e DATA1 200, com uma ocorrência e114 membros. DATA2 retornou422 com corpo67bytes: `errors=[timeout]`, `formatted_errors=[Tempo limite excedido]`. Houve exatamente3 reservas e3 conclusões HTTP, duas respostas lógicas bem-sucedidas, uma linha projetada e nenhum terminal vazio. A execução parou sem retry. A falha também com um registro impede atribuir os timeouts somente ao volume. Não há prova de429, problema de autenticação ou causa interna específica na ESL.

Fretes permanece0/3 semanas aprovadas. A comparação anterior de19/10 continua válida no seu escopo:114 colunas efetivamente comparadas,109 integralmente iguais e cinco com diferenças em três fretes atualizados depois da Source; zero conversões pendentes,847 grupos de linhas completas iguais e50 totais numéricos conferidos. Coletas mantém3/3. Nenhum valor foi alterado para forçar igualdade e dados adicionais da API continuam preservados.

## Execução e arquivos preservados

- Unidade `fretes-day12-real0681-01`, execução `7ec25491-8846-4067-9653-dd5bc66ec6e2`, per60/cap16DATA17HTTP já qualificado, gap3s depois de cada resposta/request30s/Source210s/Original300s1GiB. Aumentar paginação ainda é candidato, sem nova admissão.
- Original1,42.245ms,pico358.023.168bytes. Java16252/1 e todas as entradas/saídas encerrados; sem processos externos parados. Canônico849 foi restaurado fisicamente antes da reconciliação dos efeitos reais.
- RAW parcial privado `outputs\fretes-day12-real0681-01-actual01\capture.pending`: INFO15768bytes/2F3624, DATA1 3750bytes/DE3DE03A, DATA2erro67bytes/6EB41D27. Metadado parcial2659bytes/78FED4F4. Não promover a captura completa; ausência de terminal permanece.
- Excel oficial ESL1763622, gerado04/11às15:39, filtro12/10–12/10, CacheNão, uma linha114colunas. Cópia privada preservada em `C:\Users\suporte\Documents\Armazenamento-Projetos\dashboards-etl\fretes-day12-fresh-excel0681-01\excel\fretes-2026-10-12-export-20261104-1539.xlsx`,7136bytes, SHA `BD4D58D9C7AB5696ED221E35F98B15806DABBB9181D1D3A772062CB7AF481DB6`. Comparação de conteúdo deste novo par ainda pendente.
- Diagnóstico e cópia sob Original0/1.384ms/42.012.672bytes, nenhuma chamada externa. Somente erro sanitizado/contagens publicadas;114 valores não publicados.

## Protocolo de parcial e reconciliação

O protocolo novo vincula o orçamento firmado16/17 ou46/47, execução, revisão, envelope e assinatura existentes, controller, fechamento físico e cada artefato retido. Linhas e IDs distintos são medidos separadamente: per limita IDs, permitindo expansão de uma entidade em várias linhas. O ramo legado2HTTP/0projetadas permanece exato.

Uma suíte sintética completa de funções PowerShell passou sob Original0/5.943ms/pico74.661.888bytes; os arquivos do host foram analisados sintaticamente. Inclui3HTTP/1linha no perfil17, perfil47/per20ou30,21linhas/20IDs permitido e21IDs rejeitado, provas/contadores/EOF adulterados e o ramo legado. Não é qualificação de todo o host ou do produto Java. Node52016 verificou criptograficamente a assinatura Ed25519 existente em280ms/17.653.760bytes, somente chave pública, sem nova assinatura. Os três corpos foram mantidos e conferidos por hash no Original; foi medida uma ocorrência/um ID inteiro escalar, sem dedup.

A primeira qualificação local parou antes de Node/RAW por limite genérico64MiB do executável Node93MiB; fechamento e estado restaurados, sem Source. O sucessor usou limite explícito128MiB apenas para o runtime exato, sem mudar o limite dos demais inputs. A primeira montagem dos metadados de reconciliação parou antes de CAS por campo effect ausente no argumento temporário; corrigido no sucessor, preservando os arquivos já escritos e conferindo igualdade exata, sem repetir execução externa.

CAS Root acrescentou somente o fato parcial3/3/1 e o contador3, preservando lexicalmente TODOS os valores anteriores e a ACL. O verificador FctxFrontier conferiu o estado real e a cadeia de dois passos sem ler RAW. Não foi acrescentado par completo.

**Canônico ATUAL:** `29B61D5C51A66036738CB78794C9D68867AB82B95FE9487BE90ECBCECF7DA238`,712.907bytes,ACL B4,Root0658,vaga vazia. São17pares completos e4fatos parciais. **Não restaurar849/98CA.**

- Aceite de reconciliação parcial: `outputs\root-acceptance-fretes-closed-partial0681-01.json`,2058bytes/E22CE665644C6FAD5A68E0BCC9B2DECECB0122A3090DA58E75B121E6283A0AD6.
- Recibo: `outputs\root-reconciliation-fretes-partial-budget0681-01.json`,1291bytes/3434FA7EB8CC278EB15F40837E451F672FDB5FFF225EA9212D9B69C97F357783.
- Controle atual: `outputs\fretes-current-source-control0681-01.json`,27864bytes/678E75730CE7E1988A38D4ACFBD790AA569C3C96691E6E88FFD55C2286E942A3.
- Papel tipado: `outputs\fretes-latest-complete-main-basis0681-01.json`,1908bytes/E8E27C259F3CD3E62A259FFAA3AA49516AE9F8711C56D793BEF68AE53278D94B. Última captura completa continua a819/day19, sem rebatizar o parcial.

## Trabalho em andamento

Root qualifica o comparador genérico diário/semanal e o diagnóstico parcial, para comparar o que foi efetivamente recebido sem alegar completude. Apoio do ETLV2 termina a integração Java do orçamento finito, contagens reais de falha e ponte parcial. O host47 ainda não foi adotado; Java e testes pendentes. Nova consulta só depois de qualificação, pré-condições físicas e unidade Root própria; não repetir automaticamente a consulta falha nem ampliar velocidade/prazos por duração da sessão.

Root único executor pesado/API/browser; apoios somente código estático. V1 permanece pausado. Sem SQL, implantação, produção, contas ou permissões alterados. Depois de Fretes3/3, escolher outra entidade. Consumo/publicação real no dashboard continuam pendentes.