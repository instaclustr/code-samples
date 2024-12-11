SELECT 
    t.timestamp AS toyTimestamp, 
    t.toyType, 
    t.toyId, 
    b.timestamp AS boxTimestamp, 
    b.boxId
FROM 
    toys t
JOIN 
    boxes b
ON 
    t.toyType = b.toyType
AND 
    ABS(toUnixTimestamp(t.timestamp) - toUnixTimestamp(b.timestamp)) <= 60

