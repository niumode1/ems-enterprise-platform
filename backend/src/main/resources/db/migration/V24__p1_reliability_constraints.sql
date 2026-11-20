-- Reliability: make direct conversations unique per employee pair.
ALTER TABLE chat_conversation
    ADD COLUMN direct_key VARCHAR(64) NULL AFTER type;

CREATE TEMPORARY TABLE tmp_chat_direct AS
SELECT MIN(x.conversation_id) AS keep_id, x.lo, x.hi
FROM (
    SELECT c2.id AS conversation_id, MIN(m.employee_id) AS lo, MAX(m.employee_id) AS hi
    FROM chat_conversation c2
    JOIN chat_member m ON m.conversation_id = c2.id
    WHERE c2.type = 'DIRECT'
    GROUP BY c2.id
    HAVING COUNT(*) = 2
) x
GROUP BY x.lo, x.hi;

UPDATE chat_conversation c
JOIN tmp_chat_direct t ON t.keep_id = c.id
SET c.direct_key = CONCAT(t.lo, ':', t.hi);

DROP TEMPORARY TABLE tmp_chat_direct;

ALTER TABLE chat_conversation
    ADD UNIQUE KEY uk_chat_direct_key (direct_key);
