-- EduTrack database bootstrap.
--
-- Usage (run once, as a PostgreSQL superuser, from the repository root):
--     psql -U postgres -f scripts/setup-db.sql
--
-- You will be prompted for the password of the new "edutrack" role. The password is NOT stored
-- in this file; put the same value in ~/.edutrack/application.properties:
--     spring.datasource.password=<the password you entered>
--
-- Creates:
--     role      edutrack         (LOGIN)
--     database  edutrack         (application database, owned by edutrack)
--     database  edutrack_test    (integration test database, owned by edutrack)

\set ON_ERROR_STOP on

-- Non-interactive alternative: pass the password with  -v edutrack_pw=<password>
\if :{?edutrack_pw}
\else
\prompt 'Password for the new edutrack role: ' edutrack_pw
\endif

CREATE ROLE edutrack LOGIN PASSWORD :'edutrack_pw';

CREATE DATABASE edutrack OWNER edutrack ENCODING 'UTF8';

CREATE DATABASE edutrack_test OWNER edutrack ENCODING 'UTF8';
