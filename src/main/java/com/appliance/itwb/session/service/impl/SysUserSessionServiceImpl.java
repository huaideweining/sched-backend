package com.appliance.itwb.session.service.impl;

import com.appliance.itwb.session.config.SessionProperties;
import com.appliance.itwb.session.dto.AuthUser;
import com.appliance.itwb.session.dto.LoginRequest;
import com.appliance.itwb.session.dto.LoginResponse;
import com.appliance.itwb.session.dto.SessionExtendResult;
import com.appliance.itwb.session.dto.SessionPageQuery;
import com.appliance.itwb.session.dto.SessionPageResult;
import com.appliance.itwb.session.dto.SessionValidateResponse;
import com.appliance.itwb.session.dto.SessionVO;
import com.appliance.itwb.session.entity.SysUserSession;
import com.appliance.itwb.session.entity.enums.DeviceType;
import com.appliance.itwb.session.entity.enums.SessionStatus;
import com.appliance.itwb.session.listener.SessionEventPublisher;
import com.appliance.itwb.session.mapper.SysUserSessionMapper;
import com.appliance.itwb.session.service.ITokenService;
import com.appliance.itwb.session.service.ISysUserSessionService;
import com.appliance.itwb.session.service.IUserAuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多端登录会话服务实现，整个模块的业务核心。
 *
 * <p><b>文件作用</b>：实现"多会话并存/单点互斥 + 登录状态追踪 + 踢下线 + 超时失效控制"。
 * 所有写操作都以"条件更新 + 影响行数"的方式保证并发安全。</p>
 *
 * <p><b>依赖</b>：MyBatis-Plus、{@link SessionProperties}、{@link ITokenService}、
 * {@link IUserAuthService}、{@link SessionEventPublisher}。</p>
 *
 * <p><b>Oracle 注意点</b>：</p>
 * <ul>
 *   <li>状态判定用 SYSTIMESTAMP 放在 WHERE 里，Java 侧只做展示，不重复判定；</li>
 *   <li>Oracle 读已提交下"先 SELECT 后 UPDATE"会互相覆盖，因此这里没有一处先查后改的写操作；</li>
 *   <li>时间算术（续期）在 SQL 内完成，Oracle 的 TIMESTAMP + NUMBER 以"天"为单位；</li>
 *   <li>TIMESTAMP(6) 有毫秒，与 LocalDateTime 完全对齐，不存在 Oracle DATE 的秒级截断问题。</li>
 * </ul>
 *
 * <p><b>核心取舍（务必阅读）</b>：</p>
 * <ul>
 *   <li><b>多端并存 vs 单点</b>：默认多端并存（{@code multi-login-enabled=true}），
 *       因为 IT 同事常在多台电脑上同时查 ERP；关掉时按"同设备类型"踢旧，不跨设备互踢；</li>
 *   <li><b>校验是否写库</b>：不做"每请求一次 UPDATE"。只有剩余有效期低于
 *       {@code extend-threshold} 时才写库，其余请求只做一次等值查询。
 *       这样既避免写放大，又把"过期判定"交给原子 SQL，不依赖读到的旧数据；</li>
 *   <li><b>总生命周期上限</b>：滑动续期会把 EXPIRE_TIME 前移到
 *       min(现在+最大空闲, 登录时间+绝对上限)，防止"每天点一次就永远不超时"。</li>
 * </ul>
 */
@Service
public class SysUserSessionServiceImpl implements ISysUserSessionService {

    private static final Logger log = LoggerFactory.getLogger(SysUserSessionServiceImpl.class);

    private final SysUserSessionMapper sessionMapper;
    private final ITokenService tokenService;
    private final IUserAuthService userAuthService;
    private final SessionProperties sessionProperties;
    private final SessionEventPublisher eventPublisher;

    /**
     * 构造会话服务。
     *
     * @param sessionMapper     会话 DAO
     * @param tokenService      令牌服务
     * @param userAuthService   用户名密码校验（需按实际情况替换）
     * @param sessionProperties 会话配置
     * @param eventPublisher    事件发布器
     */
    public SysUserSessionServiceImpl(SysUserSessionMapper sessionMapper, ITokenService tokenService,
                                     IUserAuthService userAuthService, SessionProperties sessionProperties,
                                     SessionEventPublisher eventPublisher) {
        this.sessionMapper = sessionMapper;
        this.tokenService = tokenService;
        this.userAuthService = userAuthService;
        this.sessionProperties = sessionProperties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * {@inheritDoc}
     *
     * <p>顺序：身份校验 →（单点模式下）踢掉同设备旧会话 → 按上限淘汰 → 生成会话 → 落库 → 广播。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        // 认证与会话解耦：密码怎么校验由 IUserAuthService 决定，这里只消费结果
        AuthUser authUser = userAuthService.authenticate(request.getUsername(), request.getPassword());
        String userId = authUser.getUserId();

        DeviceType deviceType = DeviceType.parse(request.getDeviceType());
        if (deviceType == null) {
            deviceType = DeviceType.PC;
        }
        String deviceTypeCode = deviceType.getCode();

        LocalDateTime now = LocalDateTime.now();

        // 单点模式：先按"同设备类型"顶掉旧会话。用条件 UPDATE（不先查），
        // 两个线程同时登录同一账号时不会互相把对方的新会话误置为失效。
        if (Boolean.FALSE.equals(sessionProperties.getMultiLoginEnabled())) {
            kickSameDeviceAndNotify(userId, deviceTypeCode, userId, "单点登录：同设备旧会话被新登录顶替");
        }

        // 会话数上限：宁可淘汰最旧的，也不要无限堆积。多会话并存模式下同样生效。
        enforceMaxSessions(userId, deviceTypeCode);

        String sessionId = tokenService.generateSessionId();
        String rawToken = tokenService.generateToken();
        // 令牌只在这一行转成哈希入库；明文随响应返回后立即失去引用，不写库、不落日志

        Duration expireDuration = sessionProperties.getExpireDuration();
        boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
        if (rememberMe) {
            Duration rememberDuration = sessionProperties.getRememberMeDuration();
            if (rememberDuration != null && rememberDuration.compareTo(expireDuration) > 0) {
                expireDuration = rememberDuration;
            }
        }
        LocalDateTime expireTime = now.plus(expireDuration);

        SysUserSession entity = new SysUserSession();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setUsername(authUser.getUsername());
        entity.setDisplayName(authUser.getDisplayName());
        // 只落哈希，明文令牌随响应返回后即被 GC，不进库、不进日志
        entity.setTokenHash(tokenService.hashToken(rawToken));
        entity.setDeviceType(deviceType);
        entity.setDeviceId(request.getDeviceId());
        entity.setDeviceInfo(request.getDeviceInfo());
        entity.setIp(clientIp);
        entity.setUserAgent(userAgent);
        entity.setLoginTime(now);
        entity.setLastActiveTime(now);
        entity.setExpireTime(expireTime);
        entity.setRememberMe(rememberMe);
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setCreateBy(userId);
        entity.setCreateTime(now);
        entity.setUpdateBy(userId);
        entity.setUpdateTime(now);

        sessionMapper.insert(entity);

        int activeSessions = countActiveSessions(userId);
        log.info("[session] 登录成功 userId={}, sessionId={}, deviceType={}, activeSessions={}",
                userId, sessionId, deviceTypeCode, activeSessions);

        LoginResponse response = new LoginResponse();
        response.setToken(rawToken);
        response.setTokenType("Bearer");
        response.setSessionId(sessionId);
        response.setUserId(userId);
        response.setUsername(authUser.getUsername());
        response.setDisplayName(authUser.getDisplayName());
        response.setDeviceType(deviceTypeCode);
        response.setLoginTime(now);
        response.setExpireTime(expireTime);
        response.setMultiLoginEnabled(sessionProperties.getMultiLoginEnabled());
        response.setMaxSessions(sessionProperties.getMaxSessionsPerUser());
        response.setActiveSessions(activeSessions);
        return response;
    }

    /**
     * {@inheritDoc}
     *
     * <p>关键性能取舍：本方法在两种模式下都**不写 last_active_time**。</p>
     * <ul>
     *   <li>{@code slide=false}：只做一次按哈希的等值查询，零写操作；</li>
     *   <li>{@code slide=true}：剩余有效期 &gt;= 续期阈值时同样零写操作，只有临近过期
     *       （剩余 &lt; 阈值）才执行一次原子续期 UPDATE。</li>
     * </ul>
     * 若每次校验都写库，一个万台并发的系统每秒会产生上万次 UPDATE，Oracle 会很快吃不住。
     * 已过期的会话在这里**刻意不改 STATUS**，避免僵尸会话在每次轮询里反复写库，
     * 状态回收统一交给 10 分钟一次的清理任务。</p>
     */
    @Override
    public SessionValidateResponse validate(String token, String deviceId, boolean slide) {
        SessionValidateResponse response = new SessionValidateResponse();
        if (!StringUtils.hasText(token)) {
            response.setValid(Boolean.FALSE);
            response.setExtended(Boolean.FALSE);
            response.setReason("令牌为空");
            return response;
        }

        SysUserSession session = sessionMapper.selectByTokenHash(tokenService.hashToken(token));
        if (session == null) {
            return invalid("会话不存在或已失效");
        }

        // 逻辑状态 + 时间边界：两个条件缺一不可。
        // 可能出现 STATUS=ACTIVE 但 EXPIRE_TIME 已过（还没被清理任务回收），此时必须判死。
        if (session.getStatus() != SessionStatus.ACTIVE) {
            String statusText = session.getStatus() == null ? "未知"
                    : session.getStatus().getDescription();
            return invalid("会话状态已被置为 " + statusText);
        }
        LocalDateTime expireTime = session.getExpireTime();
        if (expireTime == null || !expireTime.isAfter(LocalDateTime.now())) {
            // 这里刻意不写库：过期判定以 EXPIRE_TIME 为准，STATUS 回收交给 10 分钟一次的清理任务。
            // 若在这里顺手改 STATUS，僵尸会话会在它死掉之后的每次轮询里都产生一次 UPDATE。
            return invalid("会话已超时失效");
        }
        if (sessionProperties.getDeviceBindingEnabled()
                && StringUtils.hasText(deviceId)
                && StringUtils.hasText(session.getDeviceId())
                && !deviceId.equals(session.getDeviceId())) {
            return invalid("令牌与当前设备不匹配");
        }

        fillSessionInfo(response, session);

        if (!slide) {
            // 只校验：不产生任何写操作，适合网关的高频探活
            response.setValid(Boolean.TRUE);
            response.setExtended(Boolean.FALSE);
            return response;
        }

        Duration remaining = Duration.between(LocalDateTime.now(), expireTime);
        if (remaining.compareTo(sessionProperties.getExtendThreshold()) >= 0) {
            // 剩余时间还够，不写库。会话由 EXPIRE_TIME 兜住，用户感知不到。
            response.setValid(Boolean.TRUE);
            response.setExtended(Boolean.FALSE);
            return response;
        }

        int rows = sessionMapper.slideExtend(session.getSessionId(),
                toSeconds(sessionProperties.getMaxIdleDuration()),
                toSeconds(sessionProperties.getExpireDuration()));
        if (rows <= 0) {
            // 0 行 = 会话已被踢下线或刚好过期，典型的"抢跑"场景，无需重试
            return invalid("会话已失效");
        }
        SessionExtendResult extendResult = sessionMapper.selectExtendResult(session.getSessionId());
        if (extendResult == null || !SessionStatus.ACTIVE.getCode().equals(extendResult.getStatus())) {
            return invalid("会话已失效");
        }
        LocalDateTime newExpire = extendResult.getExpireTime() == null
                ? expireTime : extendResult.getExpireTime();
        response.setValid(Boolean.TRUE);
        response.setExtended(Boolean.TRUE);
        response.setExpireTime(newExpire);
        response.setLastActiveTime(extendResult.getLastActiveTime() == null
                ? session.getLastActiveTime() : extendResult.getLastActiveTime());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int logout(String token, String sessionId, boolean logoutAll, String operatorId) {
        String targetSessionId = sessionId;
        if (!StringUtils.hasText(targetSessionId) && StringUtils.hasText(token)) {
            SysUserSession session = sessionMapper.selectByTokenHash(tokenService.hashToken(token));
            if (session != null) {
                targetSessionId = session.getSessionId();
            }
        }
        if (!StringUtils.hasText(targetSessionId)) {
            return 0;
        }
        SysUserSession target = sessionMapper.selectBySessionId(targetSessionId);
        if (target == null) {
            return 0;
        }
        // 登出同样只能作用于自己名下的会话：否则传他人 sessionId 就能把对方全部设备登出
        if (!ownsSession(target, operatorId)) {
            log.warn("[session] 越权登出被拒绝：operator={}, 目标会话 owner={}, sessionId={}",
                    operatorId, target.getUserId(), targetSessionId);
            return 0;
        }
        int rows;
        if (logoutAll) {
            rows = sessionMapper.kickAllByUserId(target.getUserId(), "LOGOUT",
                    "用户登出全部设备", operatorId);
            eventPublisher.publishLogout(target.getSessionId(), target.getUserId(),
                    target.getUsername(), target.getIp());
        } else {
            rows = sessionMapper.logoutBySessionId(targetSessionId, "LOGOUT", "", operatorId);
            eventPublisher.publishLogout(targetSessionId, target.getUserId(),
                    target.getUsername(), target.getIp());
        }
        log.info("[session] 登出 userId={}, sessionId={}, logoutAll={}, rows={}",
                target.getUserId(), targetSessionId, logoutAll, rows);
        return rows;
    }

    /**
     * 踢下线：单条会话。**只允许操作自己名下的会话。**
     *
     * @param sessionId   会话号
     * @param operatorId  操作人用户主键（来自服务端会话上下文，不信任前端）
     * @param remark      踢出原因
     * @return 是否真的踢掉了（false 表示会话已失效、已被别人先处理，或越权被拒）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean kick(String sessionId, String operatorId, String remark) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        SysUserSession target = sessionMapper.selectBySessionId(sessionId);
        if (target == null) {
            return false;
        }
        // 会话只能由所属用户自己操作：否则任何人拿到他人 sessionId 就能把对方踢下线
        if (!ownsSession(target, operatorId)) {
            log.warn("[session] 越权踢下线被拒绝：operator={}, 目标会话 owner={}, sessionId={}",
                    operatorId, target.getUserId(), sessionId);
            return false;
        }
        // 条件更新：只有仍为 ACTIVE 且未过期的会话才会被真正踢掉
        int rows = sessionMapper.kickBySessionId(sessionId, remark, operatorId);
        if (rows > 0) {
            eventPublisher.publishKicked(sessionId, target.getUserId(), target.getUsername(),
                    deviceCode(target.getDeviceType()), target.getDeviceInfo(), target.getIp(),
                    operatorId, remark);
        }
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int kickByDeviceType(String userId, String deviceType, String operatorId, String remark) {
        return kickSameDeviceAndNotify(userId, deviceType, operatorId, remark);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int kickAllByUserId(String userId, String operatorId, String remark) {
        int rows = sessionMapper.kickAllByUserId(userId, "ADMIN_KICK", remark, operatorId);
        if (rows > 0) {
            log.info("[session] 踢下线全部终端 userId={}, rows={}", userId, rows);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionPageResult<SessionVO> listUserSessions(String userId, SessionPageQuery query,
                                                         String currentSessionId) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<SysUserSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserSession::getUserId, userId);
        wrapper.eq(SysUserSession::getStatus, SessionStatus.ACTIVE);
        // 时间边界同样参与过滤：状态还停在 ACTIVE 但已过期的行不算"在线"
        wrapper.gt(SysUserSession::getExpireTime, LocalDateTime.now());
        if (StringUtils.hasText(query.getDeviceType())) {
            wrapper.eq(SysUserSession::getDeviceType, DeviceType.parse(query.getDeviceType()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            // 关键字命中终端描述或 IP 任一即可
            wrapper.and(cond -> cond.like(SysUserSession::getDeviceInfo, keyword)
                    .or().like(SysUserSession::getIp, keyword));
        }
        wrapper.orderByDesc(SysUserSession::getLastActiveTime);

        IPage<SysUserSession> page = sessionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<SessionVO> vos = toSessionVoList(page.getRecords(), currentSessionId);

        SessionPageResult<SessionVO> result = new SessionPageResult<>();
        result.setList(vos);
        result.setTotal(page.getTotal());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages((int) page.getPages());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SessionVO> listOnlineSessions(String userId) {
        List<SysUserSession> list = sessionMapper.selectActiveListByUserId(userId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(item -> toSessionVo(item, null)).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countActiveSessions(String userId) {
        return sessionMapper.countActiveByUserId(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionValidateResponse extend(String token, String sessionId) {
        SysUserSession session = null;
        if (StringUtils.hasText(sessionId)) {
            session = sessionMapper.selectBySessionId(sessionId);
        }
        if (session == null && StringUtils.hasText(token)) {
            session = sessionMapper.selectByTokenHash(tokenService.hashToken(token));
        }
        if (session == null) {
            return invalid("会话不存在或已失效");
        }
        if (session.getStatus() != SessionStatus.ACTIVE || !isNotExpired(session)) {
            return invalid("会话已超时失效");
        }
        // 主动续期：无条件执行一次原子写；LEAST(...) 保证不会越过总生命周期上限
        int rows = sessionMapper.slideExtend(session.getSessionId(),
                toSeconds(sessionProperties.getMaxIdleDuration()),
                toSeconds(sessionProperties.getExpireDuration()));
        if (rows <= 0) {
            return invalid("会话已失效");
        }
        SessionExtendResult extendResult = sessionMapper.selectExtendResult(session.getSessionId());
        SessionValidateResponse response = new SessionValidateResponse();
        response.setValid(Boolean.TRUE);
        response.setExtended(Boolean.TRUE);
        response.setReason("");
        if (extendResult != null) {
            response.setSessionId(session.getSessionId());
            response.setUserId(session.getUserId());
            response.setUsername(session.getUsername());
            response.setDeviceType(deviceCode(session.getDeviceType()));
            response.setLoginTime(session.getLoginTime());
            response.setExpireTime(extendResult.getExpireTime());
            response.setLastActiveTime(extendResult.getLastActiveTime());
        }
        return response;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 ROWNUM 分批更新，不给运维留一个"每次清理锁住整张会话表"的隐患。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredSessions() {
        int total = 0;
        int batchSize = sessionProperties.getCleanupBatchSize() <= 0 ? 1000 : sessionProperties.getCleanupBatchSize();
        // 多批循环，单次批内没有过期会话即退出
        for (int i = 0; i < 100; i++) {
            int rows = sessionMapper.expireExpiredBatch(batchSize);
            total += rows;
            if (rows < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("[session] 过期会话回收完成，rows={}", total);
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRetainedSessions(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        return sessionMapper.deleteBefore(LocalDateTime.now().minusDays(retentionDays));
    }

    /**
     * 判断操作人是否就是该会话的归属用户。
     *
     * @param session   目标会话
     * @param operatorId 操作人用户主键
     * @return 归属一致返回 true；操作人为空一律判为不归属（宁可拒绝）
     */
    private boolean ownsSession(SysUserSession session, String operatorId) {
        if (session == null || !StringUtils.hasText(operatorId)
                || session.getUserId() == null) {
            return false;
        }
        return operatorId.equals(session.getUserId());
    }

    /**
     * 单点登录的"踢同设备旧会话"：先取出待踢列表用于广播，再条件更新。
     *
     * @param userId    用户主键
     * @param deviceType 终端类型
     * @param operatorId 操作人
     * @param remark     踢出原因
     * @return 被踢掉的会话数
     */
    private int kickSameDeviceAndNotify(String userId, String deviceType, String operatorId, String remark) {
        List<SysUserSession> olds = sessionMapper.selectActiveListByUserId(userId);
        List<SysUserSession> targets = new ArrayList<>();
        for (SysUserSession item : olds) {
            if (item.getDeviceType() != null
                    && deviceType.equals(item.getDeviceType().getCode())) {
                targets.add(item);
            }
        }
        int rows = sessionMapper.kickByDeviceType(userId, deviceType, remark, operatorId);
        // 广播按"待踢列表"发出，是尽力而为：极端并发下个别会话可能已被别的连接先踢掉，
        // 从而收到一条并不属于自己的下线通知。客户端按推送重新拉一次列表即可自愈，
        // 为此在登录链路上拆成多条 UPDATE 反而不划算。
        for (SysUserSession item : targets) {
            eventPublisher.publishReplaced(item.getSessionId(), item.getUserId(),
                    item.getUsername(), deviceCode(item.getDeviceType()), item.getDeviceInfo(),
                    item.getIp(), operatorId);
        }
        return rows;
    }

    /**
     * 会话数上限控制：超出时按 LAST_ACTIVE_TIME 升序淘汰最旧的会话。
     *
     * <p><b>并发说明</b>：这里先查后改是故意的——超上限的会话数只需要"尽量收敛"，
     * 不要求强一致。真正的一致性由登录插入时的唯一会话号与后续校验兜住，
     * 极端并发下允许短暂超过 1~2 个会话，随后被下一轮淘汰或自然过期。</p>
     *
     * @param userId     用户主键
     * @param deviceType 本次登录的终端类型
     */
    private void enforceMaxSessions(String userId, String deviceType) {
        int max = sessionProperties.getMaxSessionsPerUser() == null ? 0 : sessionProperties.getMaxSessionsPerUser();
        if (max <= 0) {
            return;
        }
        int active = sessionMapper.countActiveByUserId(userId);
        for (int guard = 0; guard <= max; guard++) {
            if (active < max) {
                return;
            }
            SysUserSession oldest = sessionMapper.selectOldestActiveSession(userId);
            if (oldest == null) {
                return;
            }
            int rows = sessionMapper.kickBySessionId(oldest.getSessionId(),
                    "超出最大会话数限制，最旧会话被淘汰", userId);
            if (rows <= 0) {
                // 已经被别人踢掉，重新统计一次即可
                active = sessionMapper.countActiveByUserId(userId);
                continue;
            }
            eventPublisher.publishKicked(oldest.getSessionId(), oldest.getUserId(),
                    oldest.getUsername(), deviceCode(oldest.getDeviceType()), oldest.getDeviceInfo(),
                    oldest.getIp(), userId, "超出最大会话数限制，最旧会话被淘汰");
            active = sessionMapper.countActiveByUserId(userId);
        }
    }

    /**
     * 把数据库行转成列表展示对象，并标记当前终端。
     *
     * @param sessions         数据库行
     * @param currentSessionId 当前请求会话号，可为空
     * @return 视图对象列表
     */
    private List<SessionVO> toSessionVoList(List<SysUserSession> sessions, String currentSessionId) {
        if (sessions == null || sessions.isEmpty()) {
            return new ArrayList<>();
        }
        List<SessionVO> list = new ArrayList<>(sessions.size());
        for (SysUserSession item : sessions) {
            list.add(toSessionVo(item, currentSessionId));
        }
        return list;
    }

    /**
     * 实体转视图对象。
     *
     * @param session 会话实体
     * @param currentSessionId 当前请求会话号，可为空
     * @return 视图对象
     */
    private SessionVO toSessionVo(SysUserSession session, String currentSessionId) {
        SessionVO vo = new SessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setDeviceType(deviceCode(session.getDeviceType()));
        vo.setDeviceInfo(session.getDeviceInfo());
        vo.setIp(session.getIp());
        vo.setLoginTime(session.getLoginTime());
        vo.setLastActiveTime(session.getLastActiveTime());
        vo.setExpireTime(session.getExpireTime());
        vo.setStatus(session.getStatus() == null ? SessionStatus.ACTIVE.getCode()
                : session.getStatus().getCode());
        vo.setCurrent(Boolean.valueOf(StringUtils.hasText(currentSessionId)
                && currentSessionId.equals(session.getSessionId())));
        return vo;
    }

    /**
     * 填充校验响应的基础信息。
     *
     * @param response 待填充的响应
     * @param session  会话实体
     */
    private void fillSessionInfo(SessionValidateResponse response, SysUserSession session) {
        response.setSessionId(session.getSessionId());
        response.setUserId(session.getUserId());
        response.setUsername(session.getUsername());
        response.setDeviceType(deviceCode(session.getDeviceType()));
        response.setLoginTime(session.getLoginTime());
        response.setLastActiveTime(session.getLastActiveTime());
        response.setExpireTime(session.getExpireTime());
    }

    /**
     * 构造无效校验响应。
     *
     * @param reason 失败原因
     * @return 无效响应
     */
    private SessionValidateResponse invalid(String reason) {
        SessionValidateResponse response = new SessionValidateResponse();
        response.setValid(Boolean.FALSE);
        response.setExtended(Boolean.FALSE);
        response.setReason(reason);
        return response;
    }

    /**
     * 判断会话是否未过期。
     *
     * @param session 会话实体
     * @return 未过期返回 true
     */
    private boolean isNotExpired(SysUserSession session) {
        LocalDateTime expireTime = session.getExpireTime();
        return expireTime != null && expireTime.isAfter(LocalDateTime.now());
    }

    /**
     * 取终端类型代码，兼容库中的历史数据。
     *
     * @param deviceType 终端类型，可为 null
     * @return 终端类型代码，空值返回 OTHER
     */
    private String deviceCode(DeviceType deviceType) {
        return deviceType == null ? DeviceType.OTHER.getCode() : deviceType.getCode();
    }

    /**
     * Duration 转秒，用于 Oracle 的"天"为单位日期算术。
     *
     * @param duration 时长，可为 null
     * @return 秒数
     */
    private long toSeconds(Duration duration) {
        return duration == null ? 0L : duration.toSeconds();
    }
}
