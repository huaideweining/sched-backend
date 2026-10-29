package com.appliance.itwb.session.service;

import com.appliance.itwb.session.dto.LoginRequest;
import com.appliance.itwb.session.dto.LoginResponse;
import com.appliance.itwb.session.dto.SessionPageQuery;
import com.appliance.itwb.session.dto.SessionPageResult;
import com.appliance.itwb.session.dto.SessionValidateResponse;
import com.appliance.itwb.session.dto.SessionVO;

import java.util.List;

/**
 * 多端登录会话服务接口。
 *
 * <p><b>文件作用</b>：定义会话生命周期的全部能力：登录、校验、续期、登出、踢下线、
 * 在线终端查询、过期回收。</p>
 *
 * <p><b>依赖</b>：无（纯接口，实现见 SysUserSessionServiceImpl）。</p>
 *
 * <p><b>Oracle 注意点</b>：所有方法的时间比较都发生在 SQL 的 WHERE 里（用 SYSTIMESTAMP），
 * Java 侧不再重复做时间判断，避免"应用时间与数据库时间不一致"导致的边界误差。</p>
 *
 * <p><b>并发约定</b>：凡是会修改状态的写操作，一律"条件更新 + 看影响行数"，
 * 不做"先 SELECT 再 UPDATE"。Oracle 默认读已提交隔离级别，先查后改会互相覆盖。</p>
 */
public interface ISysUserSessionService {

    /**
     * 登录：校验通过后创建会话，并按配置决定是否需要顶掉同设备旧会话。
     *
     * @param request  登录请求
     * @param clientIp 客户端 IP（由 Controller 从请求头解析后传入）
     * @param userAgent 客户端 User-Agent
     * @return 登录结果，含明文令牌与会话号
     */
    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    /**
     * 校验会话有效性。
     *
     * <p>slide=true 且剩余有效期低于阈值时，会顺带做一次原子续期；
     * slide=false 时只做查询，不产生任何写操作。</p>
     *
     * @param token  明文令牌
     * @param deviceId 终端指纹，为空表示不做设备绑定校验
     * @param slide  true=校验并按需续期；false=只校验
     * @return 校验结果
     */
    SessionValidateResponse validate(String token, String deviceId, boolean slide);

    /**
     * 登出：登出当前会话；若 logoutAll 为 true 则登出该用户全部终端。
     *
     * <p><b>归属约束</b>：无论走哪个分支，目标会话必须归属 operatorId，
     * 否则直接拒绝并返回 0，不允许把别人踢下线。</p>
     *
     * @param token     明文令牌，为空时按 sessionId 处理
     * @param sessionId 会话号，可为空
     * @param logoutAll true 表示登出该用户全部终端
     * @param operatorId 操作人用户主键，来自服务端会话上下文
     * @return 登出的会话数
     */
    int logout(String token, String sessionId, boolean logoutAll, String operatorId);

    /**
     * 踢下线：单条会话。
     *
     * @param sessionId   会话号
     * @param operatorId  操作人用户主键
     * @param remark      踢出原因
     * @return 是否真的踢掉了（false 表示会话已失效或已被别人先处理）
     */
    boolean kick(String sessionId, String operatorId, String remark);

    /**
     * 踢下线：踢掉当前用户的指定终端类型（单点登录的"同设备踢旧"）。
     *
     * @param userId      用户主键
     * @param deviceType  终端类型
     * @param operatorId  操作人用户主键
     * @param remark      踢出原因
     * @return 被踢掉的会话数
     */
    int kickByDeviceType(String userId, String deviceType, String operatorId, String remark);

    /**
     * 踢下线：踢掉当前用户的全部终端。
     *
     * @param userId     用户主键
     * @param operatorId 操作人用户主键
     * @param remark     踢出原因
     * @return 被踢掉的会话数
     */
    int kickAllByUserId(String userId, String operatorId, String remark);

    /**
     * 查询用户在线终端（分页）。
     *
     * @param userId   用户主键
     * @param query    分页与筛选条件
     * @param currentSessionId 当前请求的会话号，用于标记 isCurrent
     * @return 分页结果与终端列表
     */
    SessionPageResult<SessionVO> listUserSessions(String userId, SessionPageQuery query,
                                                  String currentSessionId);

    /**
     * 查询用户当前在线终端（不分页），供登录时提示"你已在 xx 设备登录"。
     *
     * @param userId 用户主键
     * @return 在线终端会话列表，无则为空列表
     */
    List<SessionVO> listOnlineSessions(String userId);

    /**
     * 统计用户有效会话数。
     *
     * @param userId 用户主键
     * @return 有效会话数
     */
    int countActiveSessions(String userId);

    /**
     * 主动续期。
     *
     * @param token     明文令牌
     * @param sessionId 会话号，为空时优先使用令牌对应的会话
     * @return 续期结果
     */
    SessionValidateResponse extend(String token, String sessionId);

    /**
     * 清理过期会话，把状态回收为 EXPIRED。
     *
     * <p><b>建议频率</b>：10 分钟一次（与 yml 中 cleanup-cron 保持一致）。
     * 频率过高会给热点表带来无谓的写压力；频率过低则会出现"会话逻辑上已过期但状态仍是 ACTIVE"
     * 的中间态，本工程的校验逻辑对此是容忍的（EXPIRE_TIME 才是判死依据）。</p>
     *
     * @return 本次回收行数
     */
    int cleanupExpiredSessions();

    /**
     * 物理删除较早的历史会话，用于长期运行的库表瘦身。
     *
     * @param retentionDays 保留天数，超过该天数未活跃的记录被物理删除
     * @return 删除行数
     */
    int deleteRetainedSessions(int retentionDays);
}
