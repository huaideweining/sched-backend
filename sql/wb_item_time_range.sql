--------------------------------------------------------------------------------------------------
-- 文件   : wb_item_time_range.sql
-- 用途   : 为已存在的 WB_ITEM 表补加「时间段」三列（开始时间/结束时间/类型），与实体 WbItem
--          （startTime/endTime/spanType）及 ItemDTO 对齐。
-- 执行   : 通过 JDBC（参考 tools/Ddl.java 的 RunWbDdl 机制）执行；
--          ADD COLUMN 若列已存在会报 ORA-01430，执行工具已忽略，可重复执行（幂等）。
-- 说明   : Oracle 非引号标识符不区分大小写，列实际存储为 START_TIME / END_TIME / SPAN_TYPE。
--------------------------------------------------------------------------------------------------

ALTER TABLE WB_ITEM ADD START_TIME TIMESTAMP(6);   -- 时间段-开始时间
ALTER TABLE WB_ITEM ADD END_TIME   TIMESTAMP(6);   -- 时间段-结束时间
ALTER TABLE WB_ITEM ADD SPAN_TYPE  VARCHAR2(16);   -- 时间段类型 SINGLE/LONG_TERM/DAILY

COMMENT ON COLUMN WB_ITEM.START_TIME IS '时间段-开始时间';
COMMENT ON COLUMN WB_ITEM.END_TIME   IS '时间段-结束时间';
COMMENT ON COLUMN WB_ITEM.SPAN_TYPE  IS '时间段类型 SINGLE/LONG_TERM/DAILY';
