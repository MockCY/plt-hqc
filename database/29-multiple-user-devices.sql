-- Preserve all bindings and binding timestamps. Keep device ownership unique.
-- Stop older backend versions before applying: their binding operation replaces
-- every binding for a user. Start the updated backend before enabling clients.
ALTER TABLE user_device_selections
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id, device_id);
