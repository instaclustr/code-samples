WITH WindowedMatches AS (
    SELECT 
        t.timestamp AS toyTimestamp, 
        t.toyType, 
        t.toyId, 
        b.timestamp AS boxTimestamp, 
        b.boxId,
        ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp)) AS timeDifferenceInSeconds,
        tumbleStart(t.timestamp, INTERVAL 60 SECOND) AS windowStart,
        tumbleEnd(t.timestamp, INTERVAL 60 SECOND) AS windowEnd,
        ROW_NUMBER() OVER (PARTITION BY t.toyType, tumble(t.timestamp, INTERVAL 60 SECOND) ORDER BY ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp))) AS rowNum
    FROM 
        toys t
    JOIN 
        boxes b
    ON 
        t.toyType = b.toyType
    AND 
        ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp)) <= 60
)
SELECT 
    toyTimestamp, 
    toyType, 
    toyId, 
    boxTimestamp, 
    boxId, 
    timeDifferenceInSeconds,
    windowStart,
    windowEnd
FROM 
    WindowedMatches
WHERE 
    rowNum = 1
ORDER BY windowStart
