# 0226 — Resultado físico e busca documentados

P11_NATIVE_PASS_FULL_VERIFY_FAILED. 2026-09-22T01:09:45.487Z
Anterior:docs/continuidade/checkpoints/0225-p11-segunda-falha-e-pressao-de-memoria.md;SHA-256 bb1bd8feb3a0204acdac370597f09d566a81d9b890742600f0f96ff7821e6319.
A qualificada,C corrigido,B executado com duas suítes falhas,sem pacote.
Escala4 e ManifestGates4 passaram isoladamente;nenhuma alteração de teste
ou timeout. Agregados iguais;pressão física de memória observada depois
da falha(flag1),ausente na amostra final(flag0),sem causalidade comprovada.
Teto/vigência/duas correções preservados;nenhuma reserva física pendente.
Resultado:docs/catalogos/p11-fisico/resultado.json;SHA-256 6c3bfd1649afaa49e141e9a1c9beee52b8fa6cecc0ce5aee945a3472942954bf.
Busca23 fontes sanitizadas:16 requisitos externos ainda insuficientes,
origens/responsáveis por papel na matriz;39/45 e67/115 intactos.

Proximas acoes:
1. Selar sucessao dos bytes e validar historicos/trilha.
2. Conferir scans/UTF-8/diff e checkpoint final.
3. Admitir somente nova evidencia externa ou nova ordem finita para B.
Recuperacao:diff próprio;SQL rollback conferido,sem DDL ou commit.
