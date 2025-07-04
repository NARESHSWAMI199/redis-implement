package request.filters;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisSessionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RedisSessionFilter.class);

    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 30 * 60; // 30 minutes
    private JedisPool jedisPool;
    private String redisHost;
    private int redisPort;
    private String redisUsername;
    private String redisPassword;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        redisHost = filterConfig.getInitParameter("redis.host");
        String portParam = filterConfig.getInitParameter("redis.port");
        redisPort = (portParam != null && !portParam.isEmpty()) ? Integer.parseInt(portParam) : 6379; // Default Redis port

        redisUsername = filterConfig.getInitParameter("redis.username");
        redisPassword = filterConfig.getInitParameter("redis.password");
        
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(128);
        jedisPool = new JedisPool(poolConfig, redisHost, redisPort,redisUsername,redisPassword);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping(); // Check the connection
            logger.info("Connected to Redis at {}:{}", redisHost, redisPort);
        } catch (JedisConnectionException e) {
            logger.error("Cannot connect to Redis at {}:{}", redisHost, redisPort);
            throw new ServletException("Cannot connect to Redis: " + e.getMessage(), e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String sessionId = getSessionIdFromCookie(httpRequest);
        RedisBackedSession session = null;

        if (sessionId != null) {
            session = loadSessionFromRedis(sessionId, httpRequest, httpResponse);
        }

        if (session == null) {
            sessionId = generateSessionId();
            session = new RedisBackedSession(sessionId, httpRequest, httpResponse, jedisPool, DEFAULT_SESSION_TIMEOUT_SECONDS);
            setSessionIdCookie(httpResponse, sessionId, session.getMaxInactiveInterval());
            saveSessionToRedis(session); // Save the new empty session (can be optimized)
            logger.debug("Created new session with ID {}", sessionId);
        } else {
            session.setHttpServletRequest(httpRequest); // Update last accessed time
            session.setHttpServletResponse(httpResponse);
            // Update cookie max age on each request if session timeout is extended
            if (session.getMaxInactiveInterval() != DEFAULT_SESSION_TIMEOUT_SECONDS) {
                setSessionIdCookie(httpResponse, sessionId, session.getMaxInactiveInterval());
            }
            logger.debug("Loaded existing session with ID {}", sessionId);
        }

        HttpServletRequestWrapperWithSession wrapper = new HttpServletRequestWrapperWithSession(httpRequest, session);

        try {
            chain.doFilter(wrapper, httpResponse);
        } finally {
            if (session != null && session.isDirty()) {
                saveSessionToRedis(session);
                logger.debug("Saved session with ID {}", session.getId());
            }
        }
    }

    @Override
    public void destroy() {
        if (jedisPool != null) {
            jedisPool.destroy();
            logger.info("Destroyed Jedis pool");
        }
    }

    private String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setSessionIdCookie(HttpServletResponse response, String sessionId, int maxAgeSeconds) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/"); // Set path to root for broad accessibility
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
    }

    private String generateSessionId() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes());
    }

    private RedisBackedSession loadSessionFromRedis(String sessionId, HttpServletRequest request, HttpServletResponse response) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> sessionData = jedis.hgetAll("session:" + sessionId);
            if (sessionData != null && !sessionData.isEmpty()) {
                RedisBackedSession session = new RedisBackedSession(sessionId, request, response, jedisPool, DEFAULT_SESSION_TIMEOUT_SECONDS);
                session.loadAttributesFromMap(sessionData);
                return session;
            } else {
                logger.debug("Session with ID {} not found in Redis", sessionId);
            }
        } catch (JedisConnectionException e) {
            logger.error("Error loading session from Redis for ID {}: {}", sessionId, e.getMessage());
        }
        return null;
    }

    private void saveSessionToRedis(RedisBackedSession session) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> attributeMap = session.getAttributeMap();
            if (!attributeMap.isEmpty() || session.isDirty()) {
                attributeMap.forEach((field, value) -> jedis.hset("session:" + session.getId(), field, value));
            }
            jedis.expire("session:" + session.getId(), session.getMaxInactiveInterval());
            session.setDirty(false);
        } catch (JedisConnectionException e) {
            logger.error("Error saving session to Redis for ID {}: {}", session.getId(), e.getMessage());
        }
    }
}