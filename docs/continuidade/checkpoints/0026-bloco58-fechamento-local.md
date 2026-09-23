# Checkpoint 0026 — fechamento local A–D do Bloco 58

Data: 2026-09-09. Anterior: 0025-bloco58-sucessao-e-guards.md,
SHA-256 ee4a3a1fac22d7f869a6a76c08289de5133affdd4d500617375213bfef2af8a6.
Objetivo adotado: implementação, testes, regressão e fechamento A–D conforme
target/preparacao-bloco58/PROMPT-BLOCO-58.md. Estado máximo: TESTADO_NA_CAMADA.
O fechamento dos últimos bytes é comprovado exclusivamente pelo receipt final
passed=true e seus hashes. Se ausente/falho, executar as ações abaixo.

O usuário autorizou esta execução local em 09/09/2026. Mantidos todos os limites
e o inventário do checkpoint 0025. Zero API real, SQL inclusive leitura, runtime
físico, instalação, credencial, agenda, deploy/cutover, commit/push, orçamento
novo ou efeito desconhecido. Sem ledger físico e sem comando COT -Execute.
Nenhum processo próprio ativo antes dos gates finais listados abaixo.

A: 48 casos Coletas e três caminhos separados; 35 diferenças explícitas entre
decisão local e release canônico antes de staging. Confirmada normalização de
datas impossíveis em três formatters, RED preservado e correção mínima estrita.
B: 42 casos Usuários com expectativa independente, parser Relay, guard, mapper
e staging efetivo em memória; nenhum defeito produtivo adicional confirmado.
C: 109 casos COT/LOC/FRE preservados e caminhos LOC diferenciados. Só o destino
dos relatórios de dois testes históricos foi configurado para saída isolada.
D: sucessão exata e fotografias iniciais preservadas; matriz/relatórios/catálogo
atualizados. Diff/recovery/recibo do último delta serão verificados após salvar
este checkpoint, para evitar hash circular ou alegação antecipada de PASS.

Provas já observadas: focados A98/B94/C124; verify-final-01 exit 0, 1.303 testes,
zero falhas/erros, cinco skips condicionais documentados em java-result.json.
Enforcer/Spotless/Checkstyle/JaCoCo passaram. Não repetir Java por docs.
Oito gates estáticos; cadeia B58/B57/B55 privada, continuidade/trilha passaram;
nove guards atuais, 15 históricos, scanner zero e autoteste 11 passaram.
Hashes de Java, guards e closure-01 estão no checkpoint 0025. Falhas anteriores
continuam em logs e construction-notes.json, sem reclassificação de resultado.

Matriz e dependências: docs/catalogos/bloco58-local/README.md. Q-COL/Q-USR e
Q-COT/Q-LOC/Q-FRE/V2-012a precisam de oráculos independentes representativos,
bindings/scopes, garantias temporais/de tipos e autorização própria. COT conserva
input público pendente, executor e tetos B57. V2-041 requer atestado real e
autenticidade confirmada; não houve intake fictício. Bootstrap/relações e
V2-012b/c continuam exigindo provas SQL e aceite das saídas. Nenhum owner nominal
inventado. P08–P11/FAT-02/MAN não foram reabertos.

STATES → trilha → manifest devem suceder a fotografia de execução, preservando
integralmente os textos iniciais. Nenhum checkbox promovido: 67/115, 48 pendentes,
191 rotas abertas e zero AGORA. Windows/SQL permanece a direção técnica.

Próximas ações, somente se o receipt ainda não comprovar o fechamento:
1. Conferir checkpoint salvo, apontar RETOMADA, sincronizar STATES/trilha e
   Build-Manifest.ps1 -Status TESTADO_LOCAL_A_D; executar closure-final-01 e
   guards-final-01 em saídas novas, preservando os resultados anteriores.
2. Executar target/bloco58-local/Finalize.ps1: sete deltas existentes +33 novos
   contra initial, UTF-8/CRLF, diff e reverse --check sem aplicar. Conferir
   final/inventory.json, own.patch, code.patch, RECUPERACAO.md e receipt.json.
3. Reler receipt.json e seus hashes; somente então informar A–D tratado localmente.
   Se houver drift ou falha, registrar nova evidência e corrigir apenas o delta
   próprio, preservando a versão anterior e qualquer edição posterior do usuário.

Recuperação: initial/ e final/current, condicionada aos hashes finais. Proibido
restaurar o repositório pelo Git HEAD, limpar target ou aplicar reversão agora.
