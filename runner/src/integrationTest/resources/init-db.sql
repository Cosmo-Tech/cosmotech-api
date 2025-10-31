CREATE ROLE cosmotech_api_reader WITH LOGIN PASSWORD 'cosmotech_api_reader_pass';
CREATE ROLE cosmotech_api_writer WITH LOGIN PASSWORD 'cosmotech_api_writer_pass';
CREATE ROLE cosmotech_api_admin WITH LOGIN PASSWORD 'cosmotech_api_admin_pass';

CREATE SCHEMA inputs AUTHORIZATION cosmotech_api_writer;
CREATE SCHEMA outputs AUTHORIZATION cosmotech_api_writer;
GRANT USAGE ON SCHEMA inputs TO cosmotech_api_reader;
GRANT USAGE ON SCHEMA outputs TO cosmotech_api_reader;
