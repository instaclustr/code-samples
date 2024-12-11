CREATE TABLE toys ( `timestamp` DateTime, `toyType` UInt32, `toyId` UInt32 ) ENGINE = MergeTree ORDER BY timestamp

CREATE TABLE boxes ( `timestamp` DateTime, `toyType` UInt32, `boxId` UInt32 ) ENGINE = MergeTree ORDER BY timestamp

