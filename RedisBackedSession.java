package request.filters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import com.google.gson.Gson;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisBackedSession implements HttpSession {

    public final String id;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private final JedisPool jedisPool;
    public int maxInactiveInterval;
    private final long creationTime = Instant.now().toEpochMilli();
    private long lastAccessedTime = creationTime;
    private final Map<String, Object> attributes = new HashMap<>();
    private boolean isNew = true;
    private boolean isValid = true;
    private boolean dirty = false; // Flag to track if attributes have changed

    public RedisBackedSession(String id, HttpServletRequest request, HttpServletResponse response, JedisPool jedisPool, int maxInactiveInterval) {
        this.id = id;
        this.request = request;
        this.response = response;
        this.jedisPool = jedisPool;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public void setHttpServletRequest(HttpServletRequest request) {
        this.request = request;
        this.lastAccessedTime = Instant.now().toEpochMilli();
        this.isNew = false;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.expire("session:" + id, maxInactiveInterval);
        } catch (JedisConnectionException e) {
            System.err.println("Error updating session expiration in Redis: " + e.getMessage());
        }
    }

    public void setHttpServletResponse(HttpServletResponse response) {
        this.response = response;
    }

    public void loadAttributesFromMap(Map<String, String> data) {
        this.attributes.clear();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            String encodedValue = entry.getValue();
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(encodedValue);
                Object attributeValue = new ObjectInputStream(new ByteArrayInputStream(decodedBytes)).readObject();
                this.attributes.put(key, attributeValue);
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                System.err.println("Error deserializing session attribute '" + key + "': " + e.getMessage());
                System.err.println("Problematic Encoded Value: [" + encodedValue + "]");
            }
        }
        this.isNew = false;
    }

    public Map<String, String> getAttributeMap() {
        Map<String, String> data = new HashMap<>();
        for (Map.Entry<String, Object> entry : this.attributes.entrySet()) {
            try {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
                objectStream.writeObject(entry.getValue());
                objectStream.close();
                data.put(entry.getKey(), Base64.getEncoder().encodeToString(byteStream.toByteArray()));
            } catch (IOException e) {
            	ObjectFileWriter.writeClassNameViaObject(entry.getValue());
                System.err.println("Error serializing session attribute '" + entry.getKey() + "': " + e.getMessage());
            }
        }
        return data;
    }
    

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public ServletContext getServletContext() {
        return request != null ? request.getServletContext() : null;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.expire("session:" + id, interval);
        } catch (JedisConnectionException e) {
            System.err.println("Error updating session expiration in Redis: " + e.getMessage());
        }
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @SuppressWarnings("deprecation")
    @Override
    public HttpSessionContext getSessionContext() {
        return null; // Deprecated
    }

    @Override
    public Object getAttribute(String name) {
        lastAccessedTime = Instant.now().toEpochMilli();
        return attributes.get(name);
    }

    @Override
    public Object getValue(String name) {
        return getAttribute(name); // Deprecated
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        lastAccessedTime = Instant.now().toEpochMilli();
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public String[] getValueNames() {
        Set<String> names = attributes.keySet();
        return names.toArray(new String[0]); // Deprecated
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else {
            attributes.put(name, value);
            dirty = true;
            lastAccessedTime = Instant.now().toEpochMilli();
        }
    }

    @Override
    public void putValue(String name, Object value) {
        setAttribute(name, value); // Deprecated
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
        dirty = true;
        lastAccessedTime = Instant.now().toEpochMilli();
    }

    @Override
    public void removeValue(String name) {
        removeAttribute(name); // Deprecated
    }

    @Override
    public void invalidate() {
        if (isValid) {
            isValid = false;
            attributes.clear();
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del("session:" + id); // Remove from Redis
                if (response != null) {
                    Cookie cookie = new Cookie("ABC", "");
                    cookie.setMaxAge(0);
                    cookie.setPath("/"); // Use the same path as the session cookie
                    response.addCookie(cookie);
                }
            } catch (JedisConnectionException e) {
                System.err.println("Error invalidating session in Redis: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public boolean isValid() {
        return isValid;
    }
}