-- W34 G6: per-session model + effort selection
ALTER TABLE sessions ADD COLUMN model_id VARCHAR(128);
ALTER TABLE sessions ADD COLUMN effort VARCHAR(32);
