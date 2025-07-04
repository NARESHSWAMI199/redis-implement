package request.filters;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

public class HttpServletRequestWrapperWithSession extends HttpServletRequestWrapper {

    private final HttpSession session;

    public HttpServletRequestWrapperWithSession(HttpServletRequest request, HttpSession session) {
        super(request);
        this.session = session;
    }

    @Override
    public HttpSession getSession() {
        if (session != null) {
            ((RedisBackedSession) session).setHttpServletRequest((HttpServletRequest) getRequest());
        }
        return session;
    }

    @Override
    public HttpSession getSession(boolean create) {
        if (session != null) {
            ((RedisBackedSession) session).setHttpServletRequest((HttpServletRequest) getRequest());
            return session;
        }
        if (create) {
            // This branch should ideally not be reached with the current Filter logic
            throw new IllegalStateException("Creation of new session should be handled by the Filter");
        }
        return null;
    }
}