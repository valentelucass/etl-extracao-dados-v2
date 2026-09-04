# Runbook — Conter e rotacionar segredos expostos

Este runbook materializa a parte offline de V2-041. Ele não autoriza alteração de credencial, chamada externa, restart, deploy ou escrita produtiva. A tarefa permanece bloqueada até existir coordenação nominal, invalidação comprovada dos valores anteriores e saúde do único writer legado.

## Invariantes

- Considere comprometida toda credencial que possa ter aparecido no incidente, sem reler ou copiar seu valor para inventariá-la.
- Não registre em Git token, senha, login, URL de tenant, payload, cursor, documento ou identificador de negócio.
- Não execute probes, health checks remotos ou testes Raster enquanto V2-041 estiver bloqueada. Self-tests inteiramente locais continuam permitidos.
- Rotacione uma classe de credencial por vez. Preserve uma rota de recuperação previamente aprovada e nunca deixe dois writers produtivos ativos.
- Um consumidor encontrado no código é apenas candidato. Job, serviço, operador e principal vivos precisam ser confirmados pelo owner operacional.

## Inventário inicial sanitizado

| Classe | Consumidores candidatos encontrados estaticamente | Owner necessário | Evidência para encerrar |
|---|---|---|---|
| API ESL REST | nomes residuais de configuração em wrappers/launchers do legado; nenhum cliente ou step REST executável foi encontrado | owner do legado para confirmar ausência de consumidor vivo | configuração residual removida dos launchers; scanner sem achado e ausência operacional confirmada |
| API ESL GraphQL | cliente do legado, carga de Usuários e harnesses/probes de contrato da V2 | owner da integração ESL + Operações do legado | jobs dependentes saudáveis; valor anterior recusado |
| API ESL Data Export | cliente/snapshot do legado e cliente/harnesses/probes da V2 | owner da integração ESL + Operações do legado | jobs dependentes saudáveis; valor anterior recusado |
| Raster | cliente e retrofit condicionais do legado | owner Raster + Operações do legado | rotação ou desativação nominal; nenhuma sonda implícita |
| SQL Server do legado | runtime JDBC, validações e jobs operacionais | DBA + Operações do legado | conexão mínima saudável e credencial anterior invalidada sem interromper o writer |
| Sombra/testes da V2 | configuração local, migrations e harnesses opt-in | owner do ambiente V2 | segredo atualizado fora do Git e canais locais revalidados |

Os launchers candidatos incluem execução completa, loop, retrofit, expurgo noturno e fechamento mensal. Ferramentas manuais e de CI que carregam o mesmo arquivo de ambiente também entram no inventário. A lista não prova que estejam agendadas ou ativas.

## Sequência coordenada

1. Abrir ou atualizar o registro restrito do incidente com owner nominal, janela, impacto, plano de recuperação e classes de credencial afetadas. O repositório recebe apenas a referência sanitizada.
2. Inventariar consumidores vivos no scheduler, serviços, processos, secret store, CI e estações autorizadas. Registrar principal e operador somente no canal restrito.
3. Antes de mudar qualquer valor, comprovar o estado do writer legado e dos jobs dependentes. Resultado ausente, stale ou não zero bloqueia a rotação até triagem do owner.
4. Confirmar se o provedor suporta dois valores simultâneos. Se suportar, emitir o novo valor, atualizar o secret store, recarregar o consumidor de forma controlada, executar o health check aprovado e só então revogar o anterior. Se não suportar, usar uma janela nominal com rollback previamente ensaiado.
5. Fazer a verificação negativa do valor anterior por mecanismo administrativo ou teste explicitamente autorizado que não exponha o valor. Falha em provar invalidação mantém V2-041 aberta.
6. Repetir por classe de credencial. Raster exige owner e autorização próprios; a ausência dessa autorização não pode ser contornada por chamada de teste.
7. Executar a varredura redigida da árvore de trabalho. Depois do primeiro baseline Git, executar também a varredura do histórico e ligar a evidência a um SHA.
8. Registrar data, owner, consumidores verificados, resultado sanitizado, condição de rollback e referência da evidência. Só então reavaliar o checkbox de V2-041.

## Condições de parada

- Writer legado sem estado saudável verificável.
- Job obrigatório com último resultado não zero, execução concorrente ou owner desconhecido.
- Secret store, consumidor ou credencial anterior sem acesso administrativo autorizado.
- Provedor sem rollback definido ou health check aprovado.
- Qualquer saída que possa imprimir segredo, URL, payload ou dado de negócio.

## Varredura local

O gate canônico continua sendo Gitleaks com política versionada e saída redigida:

```powershell
gitleaks dir --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner .
```

Antes do scanner auxiliar real, valide suas regressões. Ele enumera o conjunto exato de candidatos do Git, inspeciona texto em UTF-8, valida o Maven Wrapper por caminho e checksum e falha fechado para arquivos desconhecidos, excessivos ou não inspecionáveis. A saída contém apenas regra, caminho, linha e inventário sanitizado — nunca o conteúdo encontrado:

```powershell
.\scripts\security\Test-OfflineSecretScan.ps1
.\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
```

Esse scanner auxiliar não substitui Gitleaks, não cobre histórico Git e não prova rotação ou invalidação. Depois do primeiro baseline, execute imediatamente a varredura histórica redigida:

```powershell
gitleaks git --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner --log-opts="--all" .
```

## Evidência sanitizada mínima

- Referência restrita do incidente e papel do owner.
- Classe da credencial, sem nome de variável quando isso ampliar a exposição.
- Quantidade de consumidores vivos por tipo, sem principal, host ou tenant.
- Data/hora da atualização, recarga, health check e invalidação.
- Resultado categórico `PASS/FAIL/BLOCKED`, código sanitizado e plano de recuperação usado.
- Comando e versão do secret scanner, escopo, SHA quando existir e quantidade de achados; relatório detalhado permanece em armazenamento restrito.
