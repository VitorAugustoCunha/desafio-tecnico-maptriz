-- Cria o banco do WebGIS no Postgres local.
-- Rode como superusuario:  sudo -u postgres psql -f scripts/setup-db.sql
--
-- A aplicacao conecta como o proprio usuario 'postgres' (ver
-- application.properties), entao nao ha role dedicada a criar. As tabelas sao
-- criadas pelo Hibernate na primeira subida do backend.

SELECT 'CREATE DATABASE webgis ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'webgis')
\gexec
