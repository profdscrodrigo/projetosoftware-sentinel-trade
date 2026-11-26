-- =====================================================================================
-- SentinelTrade — endurecimento ESPECÍFICO do PostgreSQL (executar após schema.sql, como superusuário).
-- Uso:  psql -d sentineltrade -v app_password="$APP_DB_PASSWORD" -f database/hardening-postgres.sql
-- A senha do papel da aplicação vem de variável do psql: não existe senha neste arquivo.
-- =====================================================================================

-- 1) Menor privilégio: a aplicação não é dona nem superusuária do banco.
CREATE ROLE sentinel_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE sentineltrade TO sentinel_app;
GRANT USAGE ON SCHEMA public TO sentinel_app;
GRANT SELECT, INSERT, UPDATE ON usuarios, sessoes, contas, ativos, posicoes, limites_risco, ordens,
      fila_ordens, notificacoes, parametros TO sentinel_app;
GRANT SELECT, INSERT ON execucoes TO sentinel_app;
GRANT SELECT, INSERT ON auditoria TO sentinel_app;            -- auditoria: SEM UPDATE e SEM DELETE
GRANT SELECT, INSERT, DELETE ON sessoes TO sentinel_app;

-- 2) Imutabilidade da auditoria também para superusuários "distraídos": gatilho que bloqueia UPDATE/DELETE/TRUNCATE.
CREATE OR REPLACE FUNCTION auditoria_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A trilha de auditoria é imutável (% bloqueado)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_sem_update_delete
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_imutavel();

CREATE TRIGGER trg_auditoria_sem_truncate
    BEFORE TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_imutavel();

-- 3) Execuções também são fatos imutáveis.
CREATE TRIGGER trg_execucoes_sem_update_delete
    BEFORE UPDATE OR DELETE ON execucoes
    FOR EACH ROW EXECUTE FUNCTION auditoria_imutavel();
