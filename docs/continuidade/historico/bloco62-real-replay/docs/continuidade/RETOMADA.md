# Retomada — B62: correção local de Coletas verificada

COLETAS_LOCAL_FIX_VERIFIED_REAL_REPLAY_PENDING. Pedido do usuário: encontrar e
implementar a solução, após autorizar buscar nas APIs usando .env. STATES
mantém a autoridade. Não existe pergunta de localização pendente ao usuário.

[Checkpoint0056](checkpoints/0056-bloco62-correcao-local-coletas.md),
SHA-256 a2b0aaa77bb9f38e73f8526fc414863b6e36b6c821f06cf07d1986ffe9f7ab02.
[Relatório](../catalogos/bloco62-coletas-fix/RELATORIO.md) e
[ADR0043](../adr/0043-coletas-contrato-de-captura-e-forma-por-release.md).

Corrigidos o release corrente6908 e a seleção da forma na composição HTTP:
ROOT_ARRAY,31 campos/6 filtros,metadata sem tipos inventados. Campos usados nas
regras têm tipos estritos;21 colunas restantes possuem política local de
captura escalar sem coerção. Isso não comprova seus tipos reais. Paths/containers
inesperados seguem recusados. Histórico V2-025a e laboratórios preservados.

Prova:21 testes novos do fluxo HTTP loopback/parser/gate/mapper/staging, uma
regressão da seleção, handlers oficiais com JDBC sintético. RED anterior:1 falha
esperada. Verify1427/0/0/4,Java17/heap512MiB,gates verdes,91,63%linhas/76,52%branches.
Seis checks runtime offline passaram. Três falhas iniciais de autoria de testes
foram corrigidas e preservadas. Skips:3symlinks Windows e Cotações opt-in.

Rodada própria2/2 encerrada: /info200 e /data429; sem retry/redirect nem nova
consulta depois. Dia09/09,per2,order sequence_code asc,timeout30s,duração90s,
10MiB transporte/64KiB dados. Nada de payload real persistido. B62 anterior4/4
não foi renovado. Sem SQL,UAC,runtime operacional,campanha B60 ou produção.

Correção comprovada localmente; replay real desta revisão ainda não executado.
V2-012a/b/c,Q-COL-01,Q-USR-01,V2-041 continuam abertos; Q-MAN-01 EXTERNAL_HOLD.
Não inferir paridade,rotação,completude,snapshot,exclusão ou cutover.
Roadmap67/115,48 pendentes,191 rotas,zero AGORA. Nenhum efeito desconhecido.

Sucessão: Test-Bloco62ColetasFix.ps1 -IncludePrivateEvidence -SelfTest, dez
snapshots e manifesto em docs/catalogos/bloco62-coletas-fix/. Checkpoint0055,
manifestos e ledgers anteriores preservados. Evidência privada:
target/b62-coletas-fix-20260910-163750/, incluindo before,diagnóstico,ledger,
build,red-reports,logs,java-verification,diff-completo.patch,receipt. Conferir
final-checks.json/delivery-checks.json antes de presumir gate de entrega.

Próximas ações:
1. Conferir checkpoint e gates de sucessão/entrega; correção local está concluída.
2. Após tratamento do429, delimitar uma rodada de replay da revisão; não reutilizar
   saldos encerrados e não executar SQL ou runtime operacional.
3. Continuar o oráculo independente e a janela representativa de cada rota pela
   matriz B61; manter a prova própria de V2-041 separada de acesso funcional.
