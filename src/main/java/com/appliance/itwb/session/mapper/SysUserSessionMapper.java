package com.appliance.itwb.session.mapper;

import com.appliance.itwb.session.dto.SessionExtendResult;
import com.appliance.itwb.session.entity.SysUserSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话数据访问层。
 *
 * <p><b>文件作用</b>：通用 CRUD 由 {@link BaseMapper} 提供；高频且要求原子性的动作
 * （续期、踢下线、登出、过期回收）一律用注解 SQL 直接更新，用"影响行数"做并发判定。</p>
 *
 * <p><b>依赖</b>：MyBatis-Plus BaseMapper、MyBatis @Param/@Select/@Update。</p>
 *
 * <p><b>Oracle 注意点</b>：</p>
 * <ul>
 *   <li>所有时间比较用 SYSTIMESTAMP（数据库时间），不用 Java 侧传入的时间，避免应用与库时间不一致；</li>
 *   <li>Oracle 的 DATE 无毫秒，故比较 EXPIRE_TIME 时按 TIMESTAMP(6) 语义即可；</li>
 *   <li>批量更新用 ROWNUM 限制行数，避免一次锁住过多行；</li>
 *   <li>取"最旧会话"必须用 嵌套子查询 + ROWNUM，直接写 ROWNUM &lt;= 1 加 ORDER BY 会取错行。</li>
 * </ul>
 *
 * <p><b>为什么这些 SQL 不用先 SELECT 再 UPDATE</b>：Oracle 默认读已提交隔离级别，
 * 两个请求可能读到同一份旧数据然后互相覆盖。把条件写进 WHERE，靠返回的行数判断"是否抢到"，
 * 是唯一正确的无锁并发写法。</p>
 */
public interface SysUserSessionMapper extends BaseMapper<SysUserSession> {

    /**
     * 按令牌哈希查询会话。
     *
     * @param tokenHash 令牌 SHA-256 哈希
     * @return 会话实体，未命中返回 null；命中多条时 MyBatis 抛异常，需监控告警（理论上唯一）
     */
    @Select("SELECT * FROM SYS_USER_SESSION WHERE TOKEN_HASH = #{tokenHash}")
    SysUserSession selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 按业务会话号查询会话，用于踢下线/登出前拿用户与终端信息。
     *
     * @param sessionId 会话号
     * @return 会话实体，不存在返回 null
     */
    @Select("SELECT * FROM SYS_USER_SESSION WHERE SESSION_ID = #{sessionId}")
    SysUserSession selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计某用户的有效会话数。
     *
     * @param userId 用户主键
     * @return 有效会话数
     */
    @Select("SELECT NVL(COUNT(1), 0) FROM SYS_USER_SESSION "
            + "WHERE USER_ID = #{userId} AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP")
    int countActiveByUserId(@Param("userId") String userId);

    /**
     * 取某用户最旧的有效会话，用于超出会话上限时淘汰。
     *
     * @param userId 用户主键
     * @return 最早活跃的有效会话，无则返回 null
     */
    @Select("SELECT * FROM (SELECT * FROM SYS_USER_SESSION "
            + "WHERE USER_ID = #{userId} AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP "
            + "ORDER BY LAST_ACTIVE_TIME ASC, LOGIN_TIME ASC) WHERE ROWNUM <= 1")
    SysUserSession selectOldestActiveSession(@Param("userId") String userId);

    /**
     * 按会话号置为"主动登出"。
     *
     * @param sessionId    会话号
     * @param logoutType   终态原因
     * @param remark       备注
     * @param operatorId   操作人用户主键
     * @return 影响行数，0 表示会话已失效或已被别人先处理
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'LOGOUT', LOGOUT_TYPE = #{logoutType}, "
            + "LOGOUT_TIME = SYSTIMESTAMP, LAST_ACTIVE_TIME = SYSTIMESTAMP, REMARK = #{remark}, "
            + "UPDATE_BY = #{operatorId}, UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE SESSION_ID = #{sessionId} AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP")
    int logoutBySessionId(@Param("sessionId") String sessionId, @Param("logoutType") String logoutType,
                          @Param("remark") String remark, @Param("operatorId") String operatorId);

    /**
     * 批量回收过期会话，把状态改为 EXPIRED。
     *
     * @param batchSize 单批行数
     * @return 影响行数
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'EXPIRED', LOGOUT_TYPE = 'EXPIRED', "
            + "LOGOUT_TIME = SYSTIMESTAMP, UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE STATUS = 'ACTIVE' AND EXPIRE_TIME <= SYSTIMESTAMP AND ROWNUM <= #{batchSize}")
    int expireExpiredBatch(@Param("batchSize") int batchSize);

    /**
     * 把某条已过期但状态仍为 ACTIVE 的会话回收为 EXPIRED，保持"状态"与"时间边界"一致。
     *
     * @param sessionId 会话号
     * @return 影响行数
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'EXPIRED', LOGOUT_TYPE = 'EXPIRED', "
            + "LOGOUT_TIME = SYSTIMESTAMP, UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE SESSION_ID = #{sessionId} AND STATUS = 'ACTIVE'")
    int expireBySessionId(@Param("sessionId") String sessionId);

    /**
     * 按会话号踢下线（状态置 KICKED），**并强制校验会话归属**。
     *
     * <p>这里在 SQL 层就带上 {@code USER_ID = #{operatorId}} 条件，而不是只靠 Service 里的
     * Java 判断：登录态之外的越权入口一旦将来被新增，数据库这一层仍是最后一道防线，
     * 行为是"改不动、返回 0 行"而不是"悄悄踢掉了别人的会话"。</p>
     *
     * @param sessionId   会话号
     * @param remark      踢出原因
     * @param operatorId  操作人用户主键，同时也是该会话必须归属的用户
     * @return 影响行数
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'KICKED', LOGOUT_TYPE = 'KICKED', "
            + "LOGOUT_TIME = SYSTIMESTAMP, LAST_ACTIVE_TIME = SYSTIMESTAMP, REMARK = #{remark}, "
            + "UPDATE_BY = #{operatorId}, UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE SESSION_ID = #{sessionId} AND USER_ID = #{operatorId} "
            + "AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP")
    int kickBySessionId(@Param("sessionId") String sessionId, @Param("remark") String remark,
                        @Param("operatorId") String operatorId);

    /**
     * 踢掉某用户指定类型的全部终端（单点登录的"同设备踢旧"）。
     *
     * @param userId     用户主键
     * @param deviceType 终端类型
     * @param remark     踢出原因
     * @param operatorId 操作人用户主键
     * @return 影响行数
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'KICKED', LOGOUT_TYPE = 'REPLACED', "
            + "LOGOUT_TIME = SYSTIMESTAMP, REMARK = #{remark}, UPDATE_BY = #{operatorId}, "
            + "UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE USER_ID = #{userId} AND DEVICE_TYPE = #{deviceType} AND STATUS = 'ACTIVE' "
            + "AND EXPIRE_TIME > SYSTIMESTAMP")
    int kickByDeviceType(@Param("userId") String userId, @Param("deviceType") String deviceType,
                         @Param("remark") String remark, @Param("operatorId") String operatorId);

    /**
     * 踢掉某用户的全部终端（登出所有设备 / 管理员强制下线）。
     *
     * @param userId     用户主键
     * @param logoutType 终态原因
     * @param remark     备注
     * @param operatorId 操作人用户主键
     * @return 影响行数
     */
    @Update("UPDATE SYS_USER_SESSION SET STATUS = 'KICKED', LOGOUT_TYPE = #{logoutType}, "
            + "LOGOUT_TIME = SYSTIMESTAMP, REMARK = #{remark}, UPDATE_BY = #{operatorId}, "
            + "UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE USER_ID = #{userId} AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP")
    int kickAllByUserId(@Param("userId") String userId, @Param("logoutType") String logoutType,
                        @Param("remark") String remark, @Param("operatorId") String operatorId);

    /**
     * 滑动续期：把 LAST_ACTIVE_TIME 与 EXPIRE_TIME 一起前移，但不超过"总生命周期"上限。
     *
     * <p>时间算术在 SQL 里完成（Oracle 的 TIMESTAMP + NUMBER = TIMESTAMP，单位为天），
     * 好处是幂等且原子：多行并发执行彼此不覆盖，返回 0 行即说明会话已被踢或已过期。</p>
     *
     * @param sessionId       会话号
     * @param idleSeconds     新的最大空闲时长（秒）
     * @param absoluteSeconds 总生命周期上限（秒），从 LOGIN_TIME 起算
     * @return 影响行数，1 表示续期成功，0 表示本次校验无效
     */
    @Update("UPDATE SYS_USER_SESSION SET LAST_ACTIVE_TIME = SYSTIMESTAMP, "
            + "EXPIRE_TIME = LEAST(SYSTIMESTAMP + #{idleSeconds} / 86400, "
            + "LOGIN_TIME + #{absoluteSeconds} / 86400), UPDATE_TIME = SYSTIMESTAMP "
            + "WHERE SESSION_ID = #{sessionId} AND STATUS = 'ACTIVE' AND EXPIRE_TIME > SYSTIMESTAMP")
    int slideExtend(@Param("sessionId") String sessionId, @Param("idleSeconds") long idleSeconds,
                    @Param("absoluteSeconds") long absoluteSeconds);

    /**
     * 读取续期后的会话时间信息，用于回填响应。
     *
     * @param sessionId 会话号
     * @return 状态与时间信息，无行时返回 null
     */
    @Select("SELECT STATUS AS status, LAST_ACTIVE_TIME AS lastActiveTime, "
            + "EXPIRE_TIME AS expireTime FROM SYS_USER_SESSION WHERE SESSION_ID = #{sessionId}")
    SessionExtendResult selectExtendResult(@Param("sessionId") String sessionId);

    /**
     * 查询某用户的有效会话列表（带分页），供在线终端列表使用。
     *
     * @param userId 用户主键
     * @return 有效会话列表
     */
    @Select("SELECT * FROM SYS_USER_SESSION WHERE USER_ID = #{userId} AND STATUS = 'ACTIVE' "
            + "AND EXPIRE_TIME > SYSTIMESTAMP ORDER BY LAST_ACTIVE_TIME DESC")
    List<SysUserSession> selectActiveListByUserId(@Param("userId") String userId);

    /**
     * 物理删除某个时间点之前的历史会话，用于归档清理（软状态 + 物理删除双保险）。
     *
     * @param beforeTime 时间点
     * @return 影响行数
     */
    @Update("DELETE FROM SYS_USER_SESSION WHERE LAST_ACTIVE_TIME < #{beforeTime}")
    int deleteBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
