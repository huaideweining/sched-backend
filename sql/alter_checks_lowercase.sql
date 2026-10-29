--------------------------------------------------------------------------------------------------
-- 文件   : alter_checks_lowercase.sql
-- 用途   : 将 WB_* 枚举 CHECK 约束由大写改为小写，与前端 S 模型枚举（todo/doing/.../day）对齐。
-- 执行   : 通过 RunWbDdl（JDBC）执行；DROP 若约束不存在会报 ORA-02443、ADD 若已存在会报 ORA-02261，
--          已由执行工具忽略，可重复执行。
--------------------------------------------------------------------------------------------------

ALTER TABLE WB_ITEM     DROP CONSTRAINT CK_WB_ITEM_KIND;
ALTER TABLE WB_ITEM     DROP CONSTRAINT CK_WB_ITEM_STATUS;
ALTER TABLE WB_ITEM     DROP CONSTRAINT CK_WB_ITEM_PRIORITY;
ALTER TABLE WB_PROJECT  DROP CONSTRAINT CK_WB_PROJECT_STATUS;
ALTER TABLE WB_PLAN     DROP CONSTRAINT CK_WB_PLAN_PERIOD;

ALTER TABLE WB_ITEM     ADD CONSTRAINT CK_WB_ITEM_KIND      CHECK (KIND     IN ('daily', 'task'));
ALTER TABLE WB_ITEM     ADD CONSTRAINT CK_WB_ITEM_STATUS    CHECK (STATUS   IN ('todo', 'doing', 'done', 'cancel'));
ALTER TABLE WB_ITEM     ADD CONSTRAINT CK_WB_ITEM_PRIORITY  CHECK (PRIORITY IN ('p0', 'p1', 'p2'));
ALTER TABLE WB_PROJECT  ADD CONSTRAINT CK_WB_PROJECT_STATUS CHECK (STATUS   IN ('plan', 'doing', 'test', 'done'));
ALTER TABLE WB_PLAN     ADD CONSTRAINT CK_WB_PLAN_PERIOD    CHECK (PERIOD   IN ('day', 'week', 'month'));
