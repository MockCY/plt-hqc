USE hqc_plt;

UPDATE users SET phone = NULL WHERE phone = '';

-- Review this query before applying the unique index. It should return no rows.
select phone, count(*) duplicate_count
from users
where phone is not null and phone != ''
group by phone
having count(*) > 1;

ALTER TABLE users DROP INDEX idx_users_phone;
ALTER TABLE users ADD UNIQUE KEY uk_users_phone (phone);
