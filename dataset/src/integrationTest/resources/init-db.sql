CREATE USER readusertest WITH PASSWORD 'readusertest';
CREATE USER writeusertest WITH PASSWORD 'writeusertest';
CREATE USER adminusertest WITH PASSWORD 'adminusertest';

CREATE SCHEMA inputs AUTHORIZATION writeusertest;
CREATE SCHEMA outputs AUTHORIZATION writeusertest;
GRANT USAGE ON SCHEMA inputs TO readusertest;
GRANT USAGE ON SCHEMA outputs TO readusertest;
