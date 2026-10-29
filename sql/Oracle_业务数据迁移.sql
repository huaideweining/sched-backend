--------------------------------------------------------------------------------------------------
-- 文件       : Oracle_业务数据迁移.sql
-- 适用数据库 : Oracle 12c+（与 SYS_USER_SESSION 同 schema：jddba@172.16.255.9:1521/orcl）
-- 配套代码   : com.appliance.itwb.wb.*（Spring Boot 3.0.5 + MyBatis-Plus 3.5.5.x）
-- 目标       : 把前端 IT 开发工作台的 localStorage 业务数据（待办/灵感速记/项目/成员/计划/设置）
--              迁移到 Oracle，按登录用户隔离（每行业务数据带 USER_ID）。
-- 设计要点   : 1) 业务主键沿用前端生成的 uid（VARCHAR2(64)，IdType.INPUT），不引序列；
--              2) 时间列统一 TIMESTAMP(6)；lastRemind 用 NUMBER(19) 存毫秒，规避时区；
--              3) 不建跨表 FK 级联（引用完整性由应用维护，避免清空/删除异常）；
--              4) 所有表的 USER_ID 都建索引，支撑"按用户整表读写"的快照语义。
-- 说明       : 脚本可重复执行；约束与索引与建表分开写，便于按需裁剪。已存在的表用
--              DROP TABLE ... PURGE 先删后建（个人工作台数据量极小，可接受）。
--------------------------------------------------------------------------------------------------

-- ==================================================================================================
-- 零、可重复执行：先删除已存在的 WB_* 表
--    说明：表不存在时报 ORA-00942 属正常，由执行工具（JDBC / sqlplus）忽略即可。
--    改用 8 条独立 DROP（而非 PL/SQL 循环），便于 JDBC 按分号整段执行。
-- ==================================================================================================
DROP TABLE WB_MEMBER       PURGE;
DROP TABLE WB_ITEM         PURGE;
DROP TABLE WB_PROJECT      PURGE;
DROP TABLE WB_PROJECT_NODE PURGE;
DROP TABLE WB_NOTE         PURGE;
DROP TABLE WB_PLAN         PURGE;
DROP TABLE WB_PLAN_ITEM    PURGE;
DROP TABLE WB_SETTINGS     PURGE;

-- ==================================================================================================
-- 一、业务数据表（WB_*）
-- ==================================================================================================

-- 1) WB_MEMBER —— 成员（对应前端 S.members[]）
CREATE TABLE WB_MEMBER
(
    ID          VARCHAR2(64)   NOT NULL,   -- 前端 uid
    USER_ID     VARCHAR2(64)   NOT NULL,   -- 归属用户
    NAME        VARCHAR2(128 CHAR) NOT NULL,
    ROLE        VARCHAR2(64),
    COLOR       VARCHAR2(32),
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_MEMBER PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_MEMBER            IS '工作台成员表：团队成员，看板/负载/归属展示依赖';
COMMENT ON COLUMN WB_MEMBER.ID         IS '前端生成的 uid，业务主键';
COMMENT ON COLUMN WB_MEMBER.USER_ID    IS '归属用户主键';
COMMENT ON COLUMN WB_MEMBER.NAME       IS '姓名';
COMMENT ON COLUMN WB_MEMBER.ROLE       IS '角色';
COMMENT ON COLUMN WB_MEMBER.COLOR      IS '颜色标记';

-- 2) WB_ITEM —— 统一待办/事项（对应前端 S.items[]，含 daily + task）
CREATE TABLE WB_ITEM
(
    ID           VARCHAR2(64)   NOT NULL,
    USER_ID      VARCHAR2(64)   NOT NULL,
    KIND         VARCHAR2(16)   NOT NULL,   -- DAILY / TASK
    TITLE        VARCHAR2(500 CHAR) NOT NULL,
    TYPE         VARCHAR2(32),              -- daily 子类型：需求/缺陷/优化/会议/其他
    PROJECT_ID   VARCHAR2(64),              -- 关联 WB_PROJECT.ID
    ASSIGNEE     VARCHAR2(64),              -- 关联 WB_MEMBER.ID
    PRIORITY     VARCHAR2(8)    NOT NULL,   -- P0 / P1 / P2
    DUE_TIME     TIMESTAMP(6),              -- 截止时间（原 S.items[].due）
    STATUS       VARCHAR2(16)   NOT NULL,   -- TODO / DOING / DONE / CANCEL
    NOTE         VARCHAR2(4000),
    CREATED_AT   TIMESTAMP(6),
    COMPLETED_AT TIMESTAMP(6),
    LAST_REMIND  NUMBER(19),                -- 上次提醒时间（原毫秒戳），用 NUMBER 规避时区
    CREATE_BY    VARCHAR2(64),
    CREATE_TIME  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY    VARCHAR2(64),
    UPDATE_TIME  TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_ITEM PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_ITEM             IS '工作台统一待办/事项表：日常工作(daily)与开发任务(task)同一张表';
COMMENT ON COLUMN WB_ITEM.KIND        IS 'DAILY 日常工作 / TASK 开发任务';
COMMENT ON COLUMN WB_ITEM.PRIORITY    IS 'P0 紧急 / P1 重要 / P2 常规';
COMMENT ON COLUMN WB_ITEM.STATUS      IS 'TODO 待办 / DOING 进行中 / DONE 已完成 / CANCEL 已取消';
COMMENT ON COLUMN WB_ITEM.DUE_TIME    IS '截止时间';
COMMENT ON COLUMN WB_ITEM.LAST_REMIND IS '上次提醒时间（epoch 毫秒），NUMBER 存储避免时区误差';

-- 3) WB_PROJECT —— 项目（对应前端 S.projects[]）
CREATE TABLE WB_PROJECT
(
    ID          VARCHAR2(64)   NOT NULL,
    USER_ID     VARCHAR2(64)   NOT NULL,
    NAME        VARCHAR2(200 CHAR) NOT NULL,
    STATUS      VARCHAR2(16)   NOT NULL,   -- PLAN / DOING / TEST / DONE
    OWNER       VARCHAR2(64),              -- 负责人，关联 WB_MEMBER.ID
    START_DATE  TIMESTAMP(6),              -- 开始（原 S.projects[].start，ymd）
    END_DATE    TIMESTAMP(6),              -- 结束（原 S.projects[].end，ymd）
    NOTE        VARCHAR2(4000),
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_PROJECT PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_PROJECT          IS '工作台项目表';
COMMENT ON COLUMN WB_PROJECT.STATUS   IS 'PLAN 计划中 / DOING 进行中 / TEST 验收中 / DONE 已完成';
COMMENT ON COLUMN WB_PROJECT.OWNER    IS '负责人，关联 WB_MEMBER.ID';
COMMENT ON COLUMN WB_PROJECT.START_DATE IS '项目开始日期';
COMMENT ON COLUMN WB_PROJECT.END_DATE IS '项目结束日期';

-- 4) WB_PROJECT_NODE —— 项目关键节点（对应前端 S.projects[].nodes[]）
CREATE TABLE WB_PROJECT_NODE
(
    ID          VARCHAR2(64)   NOT NULL,
    USER_ID     VARCHAR2(64)   NOT NULL,
    PROJECT_ID  VARCHAR2(64)   NOT NULL,   -- 关联 WB_PROJECT.ID
    TITLE       VARCHAR2(500 CHAR) NOT NULL,
    DUE         TIMESTAMP(6),              -- 节点截止（原 ymd 或 ymdThh:mm）
    DONE        NUMBER(1) DEFAULT 0 NOT NULL CHECK (DONE IN (0, 1)),
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_PROJECT_NODE PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_PROJECT_NODE     IS '工作台项目关键节点表';
COMMENT ON COLUMN WB_PROJECT_NODE.PROJECT_ID IS '关联 WB_PROJECT.ID';
COMMENT ON COLUMN WB_PROJECT_NODE.DONE IS '0 未完成 / 1 已完成';

-- 5) WB_NOTE —— 灵感速记（新增功能）
CREATE TABLE WB_NOTE
(
    ID          VARCHAR2(64)   NOT NULL,
    USER_ID     VARCHAR2(64)   NOT NULL,
    TITLE       VARCHAR2(500 CHAR),
    CONTENT     CLOB,                     -- 正文长文本
    TAGS        VARCHAR2(500),            -- 标签，逗号分隔
    PINNED      NUMBER(1) DEFAULT 0 NOT NULL CHECK (PINNED IN (0, 1)),
    CREATED_AT  TIMESTAMP(6),
    UPDATED_AT  TIMESTAMP(6),
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_NOTE PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_NOTE         IS '工作台灵感速记表（新增功能）';
COMMENT ON COLUMN WB_NOTE.TITLE   IS '标题，可空（仅记一句话也行）';
COMMENT ON COLUMN WB_NOTE.CONTENT IS '正文，CLOB 长文本';
COMMENT ON COLUMN WB_NOTE.TAGS    IS '标签，逗号分隔';
COMMENT ON COLUMN WB_NOTE.PINNED  IS '0 普通 / 1 置顶';

-- 6) WB_PLAN —— 计划（对应前端 S.plans[]）
CREATE TABLE WB_PLAN
(
    ID          VARCHAR2(64)   NOT NULL,
    USER_ID     VARCHAR2(64)   NOT NULL,
    TITLE       VARCHAR2(500 CHAR) NOT NULL,
    PERIOD      VARCHAR2(16)   NOT NULL,   -- DAY / WEEK / MONTH
    CREATED_AT  TIMESTAMP(6),
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_PLAN PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_PLAN        IS '工作台计划表：日/周/月总结与计划';
COMMENT ON COLUMN WB_PLAN.PERIOD IS 'DAY 日 / WEEK 周 / MONTH 月';

-- 7) WB_PLAN_ITEM —— 计划项（对应前端 S.plans[].items[]）
CREATE TABLE WB_PLAN_ITEM
(
    ID          VARCHAR2(64)   NOT NULL,
    USER_ID     VARCHAR2(64)   NOT NULL,
    PLAN_ID     VARCHAR2(64)   NOT NULL,   -- 关联 WB_PLAN.ID
    TEXT        VARCHAR2(1000 CHAR),
    DONE        NUMBER(1) DEFAULT 0 NOT NULL CHECK (DONE IN (0, 1)),
    SEQ         NUMBER,
    CREATE_BY   VARCHAR2(64),
    CREATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_BY   VARCHAR2(64),
    UPDATE_TIME TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_PLAN_ITEM PRIMARY KEY (ID)
);
COMMENT ON TABLE  WB_PLAN_ITEM       IS '工作台计划项表';
COMMENT ON COLUMN WB_PLAN_ITEM.PLAN_ID IS '关联 WB_PLAN.ID';
COMMENT ON COLUMN WB_PLAN_ITEM.DONE IS '0 未完成 / 1 已完成';

-- 8) WB_SETTINGS —— 用户设置（对应前端 S.settings{}，每用户一行）
CREATE TABLE WB_SETTINGS
(
    USER_ID          VARCHAR2(64)   NOT NULL,   -- 主键即用户
    WARN_HOURS       NUMBER(3) DEFAULT 24 NOT NULL,
    WEBHOOK          VARCHAR2(1000),
    PROXY            VARCHAR2(500),
    DIGEST_ON        NUMBER(1) DEFAULT 0 NOT NULL CHECK (DIGEST_ON IN (0, 1)),
    NOTIFY_ON        NUMBER(1) DEFAULT 0 NOT NULL CHECK (NOTIFY_ON IN (0, 1)),
    DIGEST_AT        VARCHAR2(8) DEFAULT '18:00',
    LAST_DIGEST_DATE VARCHAR2(10),
    CREATE_TIME      TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_TIME      TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_WB_SETTINGS PRIMARY KEY (USER_ID)
);
COMMENT ON TABLE  WB_SETTINGS            IS '工作台用户设置表：每用户一行';
COMMENT ON COLUMN WB_SETTINGS.WARN_HOURS IS '临期窗口（小时）';
COMMENT ON COLUMN WB_SETTINGS.WEBHOOK    IS '企微机器人地址';
COMMENT ON COLUMN WB_SETTINGS.DIGEST_ON  IS '企微推送开关 0/1';
COMMENT ON COLUMN WB_SETTINGS.NOTIFY_ON  IS '桌面通知开关 0/1';
COMMENT ON COLUMN WB_SETTINGS.DIGEST_AT  IS '每日汇总时间 HH24:MI';
COMMENT ON COLUMN WB_SETTINGS.LAST_DIGEST_DATE IS '上次汇总日期 ymd';

-- ==================================================================================================
-- 二、索引：支撑"按 USER_ID 整表读写"的快照语义 + 高频筛选
-- ==================================================================================================
CREATE INDEX IDX_WB_MEMBER_USER      ON WB_MEMBER      (USER_ID);
CREATE INDEX IDX_WB_ITEM_USER_STATUS ON WB_ITEM        (USER_ID, STATUS);
CREATE INDEX IDX_WB_ITEM_USER_DUE    ON WB_ITEM        (USER_ID, DUE_TIME);
CREATE INDEX IDX_WB_PROJECT_USER     ON WB_PROJECT     (USER_ID);
CREATE INDEX IDX_WB_NODE_USER_PROJ   ON WB_PROJECT_NODE(USER_ID, PROJECT_ID);
CREATE INDEX IDX_WB_NOTE_USER_CT     ON WB_NOTE        (USER_ID, CREATED_AT);
CREATE INDEX IDX_WB_PLAN_USER        ON WB_PLAN        (USER_ID);
CREATE INDEX IDX_WB_PLANITEM_USER    ON WB_PLAN_ITEM   (USER_ID, PLAN_ID);

-- ==================================================================================================
-- 三、枚举 CHECK 约束（防御脏数据；与前端映射的固定取值域一致）
-- ==================================================================================================
-- 注意：前端 S 模型的枚举值统一为小写（见《数据库迁移设计.md》），CHECK 必须与前端取值域一致，
-- 否则任何一次真实写入都会被 ORA-02290 拒绝。下列约束统一用小写。
ALTER TABLE WB_ITEM        ADD CONSTRAINT CK_WB_ITEM_KIND     CHECK (KIND     IN ('daily', 'task'));
ALTER TABLE WB_ITEM        ADD CONSTRAINT CK_WB_ITEM_STATUS   CHECK (STATUS   IN ('todo', 'doing', 'done', 'cancel'));
ALTER TABLE WB_ITEM        ADD CONSTRAINT CK_WB_ITEM_PRIORITY CHECK (PRIORITY IN ('p0', 'p1', 'p2'));
ALTER TABLE WB_PROJECT     ADD CONSTRAINT CK_WB_PROJECT_STATUS CHECK (STATUS IN ('plan', 'doing', 'test', 'done'));
ALTER TABLE WB_PLAN        ADD CONSTRAINT CK_WB_PLAN_PERIOD   CHECK (PERIOD IN ('day', 'week', 'month'));

-- ==================================================================================================
-- 四、执行顺序提示（sqlplus）
--   1) 以 jddba 连接：sqlplus jddba/口令@//172.16.255.9:1521/orcl
--   2) 执行本脚本：@Oracle_业务数据迁移.sql
--   3) 若需重建：脚本开头已含 DROP TABLE ... PURGE（首次执行会因表不存在报 ORA-00942，可忽略或先建）。
-- ==================================================================================================
