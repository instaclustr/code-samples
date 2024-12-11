WITH RecentMatches AS (
    SELECT 
        t.timestamp AS toyTimestamp, 
        t.toyType, 
        t.toyId, 
        b.timestamp AS boxTimestamp, 
        b.boxId,
        ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp)) AS timeDifferenceInSeconds,
        ROW_NUMBER() OVER (PARTITION BY t.toyType ORDER BY ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp))) AS rowNum
    FROM 
        toys t
    JOIN 
        boxes b
    ON 
        t.toyType = b.toyType
    AND 
        ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp)) <= 60
    WHERE 
        t.timestamp >= now() - INTERVAL 60 SECOND
)
SELECT 
    toyTimestamp, 
    toyType, 
    toyId, 
    boxTimestamp, 
    boxId, 
    timeDifferenceInSeconds
FROM 
    RecentMatches
WHERE 
    rowNum = 1
