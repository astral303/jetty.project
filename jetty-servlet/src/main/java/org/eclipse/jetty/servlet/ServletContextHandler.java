// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.servlet;

import java.security.AccessController;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HandlerContainer;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;


/* ------------------------------------------------------------ */
/** Servlet Context.
 * This extension to the ContextHandler allows for
 * simple construction of a context with ServletHandler and optionally
 * session and security handlers, et.<pre>
 *   new ServletContext("/context",Context.SESSIONS|Context.NO_SECURITY);
 * </pre>
 * <p/>
 * This class should have been called ServletContext, but this would have
 * cause confusion with {@link ServletContext}.
 */
public class ServletContextHandler extends ContextHandler
{   
    public final static int SESSIONS=1;
    public final static int SECURITY=2;
    public final static int NO_SESSIONS=0;
    public final static int NO_SECURITY=0;
    
    protected Class<? extends SecurityHandler> _defaultSecurityHandlerClass=org.eclipse.jetty.security.ConstraintSecurityHandler.class;
    protected SessionHandler _sessionHandler;
    protected SecurityHandler _securityHandler;
    protected ServletHandler _servletHandler;
    protected int _options;
    protected Injector _injector;
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler()
    {
        this(null,null,null,null,null);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(int options)
    {
        this(null,null,options);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath)
    {
        this(parent,contextPath,null,null,null,null);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, int options)
    {
        this(parent,contextPath,null,null,null,null);
        _options=options;
    }
    
    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, boolean sessions, boolean security)
    {
        this(parent,contextPath,(sessions?SESSIONS:0)|(security?SECURITY:0));
    }

    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler)
    {   
        this(parent,null,sessionHandler,securityHandler,servletHandler,errorHandler);
    }

    /* ------------------------------------------------------------ */
    public ServletContextHandler(HandlerContainer parent, String contextPath, SessionHandler sessionHandler, SecurityHandler securityHandler, ServletHandler servletHandler, ErrorHandler errorHandler)
    {   
        super((ContextHandler.Context)null);
        _scontext = new Context();
        _sessionHandler = sessionHandler;
        _securityHandler = securityHandler;
        _servletHandler = servletHandler;
            
        if (errorHandler!=null)
            setErrorHandler(errorHandler);

        if (contextPath!=null)
            setContextPath(contextPath);

        if (parent instanceof HandlerWrapper)
            ((HandlerWrapper)parent).setHandler(this);
        else if (parent instanceof HandlerCollection)
            ((HandlerCollection)parent).addHandler(this);
    }    

    
    /* ------------------------------------------------------------ */
    /** Get the defaultSecurityHandlerClass.
     * @return the defaultSecurityHandlerClass
     */
    public Class<? extends SecurityHandler> getDefaultSecurityHandlerClass()
    {
        return _defaultSecurityHandlerClass;
    }

    /* ------------------------------------------------------------ */
    /** Set the defaultSecurityHandlerClass.
     * @param defaultSecurityHandlerClass the defaultSecurityHandlerClass to set
     */
    public void setDefaultSecurityHandlerClass(Class<? extends SecurityHandler> defaultSecurityHandlerClass)
    {
        _defaultSecurityHandlerClass = defaultSecurityHandlerClass;
    }

    /* ------------------------------------------------------------ */
    protected SessionHandler newSessionHandler()
    {
        return new SessionHandler();
    }
    
    /* ------------------------------------------------------------ */
    protected SecurityHandler newSecurityHandler()
    {
        try
        {
            return (SecurityHandler)_defaultSecurityHandlerClass.newInstance();
        }
        catch(Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /* ------------------------------------------------------------ */
    protected ServletHandler newServletHandler()
    {
        return new ServletHandler();
    }

    /* ------------------------------------------------------------ */
    /**
     * Finish constructing handlers and link them together.
     * 
     * @see org.eclipse.jetty.server.handler.ContextHandler#startContext()
     */
    protected void startContext() throws Exception
    {
        // force creation of missing handlers.
        getSessionHandler();
        getSecurityHandler();
        getServletHandler();
        
        Handler handler = _servletHandler;
        if (_securityHandler!=null)
        {
            _securityHandler.setHandler(handler);
            handler=_securityHandler;
        }
        
        if (_sessionHandler!=null)
        {
            _sessionHandler.setHandler(handler);
            handler=_sessionHandler;
        }
        
        setHandler(handler);
        
    	super.startContext();

    	// OK to Initialize servlet handler now
    	if (_servletHandler != null && _servletHandler.isStarted())
    		_servletHandler.initialize();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the securityHandler.
     */
    public SecurityHandler getSecurityHandler()
    {
        if (_securityHandler==null && (_options&SECURITY)!=0 && !isStarted()) 
            _securityHandler=newSecurityHandler();
        
        return _securityHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the servletHandler.
     */
    public ServletHandler getServletHandler()
    {
        if (_servletHandler==null && !isStarted()) 
            _servletHandler=newServletHandler();
        return _servletHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionHandler.
     */
    public SessionHandler getSessionHandler()
    {
        if (_sessionHandler==null && (_options&SESSIONS)!=0 && !isStarted()) 
            _sessionHandler=newSessionHandler();
        return _sessionHandler;
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public ServletHolder addServlet(String className,String pathSpec)
    {
        return getServletHandler().addServletWithMapping(className, pathSpec);
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public ServletHolder addServlet(Class<? extends Servlet> servlet,String pathSpec)
    {
        return getServletHandler().addServletWithMapping(servlet.getName(), pathSpec);
    }
    
    /* ------------------------------------------------------------ */
    /** conveniance method to add a servlet.
     */
    public void addServlet(ServletHolder servlet,String pathSpec)
    {
        getServletHandler().addServletWithMapping(servlet, pathSpec);
    }

    /* ------------------------------------------------------------ */
    /** conveniance method to add a filter
     */
    public void addFilter(FilterHolder holder,String pathSpec,EnumSet<DispatcherType> dispatches)
    {
        getServletHandler().addFilterWithMapping(holder,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /** convenience method to add a filter
     */
    public FilterHolder addFilter(Class<? extends Filter> filterClass,String pathSpec,EnumSet<DispatcherType> dispatches)
    {
        return getServletHandler().addFilterWithMapping(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /** convenience method to add a filter
     */
    public FilterHolder addFilter(String filterClass,String pathSpec,EnumSet<DispatcherType> dispatches)
    {
        return getServletHandler().addFilterWithMapping(filterClass,pathSpec,dispatches);
    }

    /* ------------------------------------------------------------ */
    /**
     * @param sessionHandler The sessionHandler to set.
     */
    public void setSessionHandler(SessionHandler sessionHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _sessionHandler = sessionHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param securityHandler The {@link org.eclipse.jetty.server.handler.SecurityHandler} to set on this context.
     */
    public void setSecurityHandler(SecurityHandler securityHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _securityHandler = securityHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param servletHandler The servletHandler to set.
     */
    public void setServletHandler(ServletHandler servletHandler)
    {
        if (isStarted())
            throw new IllegalStateException("STARTED");
        
        _servletHandler = servletHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The inject used to resource inject new Filters, Servlets and EventListeners
     */
    public Injector getInjector()
    {
        return _injector;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param injector The inject used to resource inject new Filters, Servlets and EventListeners
     */
    public void setInjector(Injector injector)
    {
        _injector = injector;
    }

    /* ------------------------------------------------------------ */
    public class Context extends ContextHandler.Context
    {

        /* ------------------------------------------------------------ */
        /* 
         * @see javax.servlet.ServletContext#getNamedDispatcher(java.lang.String)
         */
        @Override
        public RequestDispatcher getNamedDispatcher(String name)
        {
            ContextHandler context=org.eclipse.jetty.servlet.ServletContextHandler.this;
            if (_servletHandler==null || _servletHandler.getServlet(name)==null)
                return null;
            return new Dispatcher(context, name);
        }
        
        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, java.lang.Class)
         */
        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setHeldClass(filterClass);
            handler.addFilter(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, java.lang.String)
         */
        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, String className)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setClassName(className);
            handler.addFilter(holder);
            return holder.getRegistration();
        }


        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addFilter(java.lang.String, javax.servlet.Filter)
         */
        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
        {
            if (isStarted())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final FilterHolder holder= handler.newFilterHolder();
            holder.setName(filterName);
            holder.setFilter(filter);
            handler.addFilter(holder);
            return holder.getRegistration();
        }
        
        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addServlet(java.lang.String, java.lang.Class)
         */
        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setHeldClass(servletClass);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        /**
         * @see javax.servlet.ServletContext#addServlet(java.lang.String, java.lang.String)
         */
        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, String className)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setClassName(className);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
        {
            if (!isStarting())
                throw new IllegalStateException();

            final ServletHandler handler = ServletContextHandler.this.getServletHandler();
            final ServletHolder holder= handler.newServletHolder();
            holder.setName(servletName);
            holder.setServlet(servlet);
            handler.addServlet(holder);
            return holder.getRegistration();
        }

        /* ------------------------------------------------------------ */
        @Override
        public boolean setInitParameter(String name, String value)
        {
            // TODO other started conditions
            if (!isStarting())
                throw new IllegalStateException();
            return super.setInitParameter(name,value);
        }

        /* ------------------------------------------------------------ */
        @Override
        public <T extends Filter> T createFilter(Class<T> c) throws ServletException
        {
            try
            {
                T f = c.newInstance();
                if (_injector!=null)
                    f=_injector.injectFilter(f);
                return f;
            }
            catch (InstantiationException e)
            {
                throw new ServletException(e);
            }
            catch (IllegalAccessException e)
            {
                throw new ServletException(e);
            }
        }

        /* ------------------------------------------------------------ */
        @Override
        public <T extends Servlet> T createServlet(Class<T> c) throws ServletException
        {
            try
            {
                T s = c.newInstance();
                if (_injector!=null)
                    s=_injector.injectServlet(s);
                return s;
            }
            catch (InstantiationException e)
            {
                throw new ServletException(e);
            }
            catch (IllegalAccessException e)
            {
                throw new ServletException(e);
            }
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
        {
            if (_sessionHandler!=null)
                return _sessionHandler.getSessionManager().getDefaultSessionTrackingModes();
            return null;
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
        {
            if (_sessionHandler!=null)
                return _sessionHandler.getSessionManager().getEffectiveSessionTrackingModes();
            return null;
        }

        @Override
        public FilterRegistration getFilterRegistration(String filterName)
        {
            final FilterHolder holder=ServletContextHandler.this.getServletHandler().getFilter(filterName);
            return (holder==null)?null:holder.getRegistration();
        }

        @Override
        public Map<String, FilterRegistration> getFilterRegistrations()
        {
            HashMap<String, FilterRegistration> registrations = new HashMap<String, FilterRegistration>();
            ServletHandler handler=ServletContextHandler.this.getServletHandler();
            FilterHolder[] holders=handler.getFilters();
            if (holders!=null)
            {
                for (FilterHolder holder : holders)
                    registrations.put(holder.getName(),holder.getRegistration());
            }
            return registrations;
        }

        @Override
        public ServletRegistration getServletRegistration(String servletName)
        {
            final ServletHolder holder=ServletContextHandler.this.getServletHandler().getServlet(servletName);
            return (holder==null)?null:holder.getRegistration();
        }

        @Override
        public Map<String, ServletRegistration> getServletRegistrations()
        {
            HashMap<String, ServletRegistration> registrations = new HashMap<String, ServletRegistration>();
            ServletHandler handler=ServletContextHandler.this.getServletHandler();
            ServletHolder[] holders=handler.getServlets();
            if (holders!=null)
            {
                for (ServletHolder holder : holders)
                    registrations.put(holder.getName(),holder.getRegistration());
            }
            return registrations;
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig()
        {
            // TODO other started conditions
            if (_sessionHandler!=null)
                return _sessionHandler.getSessionManager().getSessionCookieConfig();
            return null;
        }

        @Override
        public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes)
        {
            // TODO other started conditions
            if (!isStarting())
                throw new IllegalStateException();
            
            if (_sessionHandler!=null)
                _sessionHandler.getSessionManager().setSessionTrackingModes(sessionTrackingModes);
        }

        @Override
        public void addListener(String className)
        {
            // TODO other started conditions
            if (!isStarting())
                throw new IllegalStateException();
            super.addListener(className);
        }

        @Override
        public <T extends EventListener> void addListener(T t)
        {
            // TODO other started conditions
            if (!isStarting())
                throw new IllegalStateException();
            super.addListener(t);
        }

        @Override
        public void addListener(Class<? extends EventListener> listenerClass)
        {
            // TODO other started conditions
            if (!isStarting())
                throw new IllegalStateException();
            super.addListener(listenerClass);
        }

        @Override
        public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException
        {
            try
            {
                T l = super.createListener(clazz);
                if (_injector!=null)
                    l=_injector.injectListener(l);
                return l;
            }
            catch(ServletException e)
            {
                throw e;
            }
            catch(Exception e)
            {
                throw new ServletException(e);
            }
        }

        @Override
        public int getEffectiveMajorVersion()
        {
            // TODO
            return 3;
        }

        @Override
        public int getEffectiveMinorVersion()
        {
            // TODO
            return 0;
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor()
        {
            // TODO
            return null;
        }
    }
    
    public interface Injector
    {
        public <T extends Filter> T injectFilter(T filter) throws ServletException;
        public <T extends Servlet> T injectServlet(T servlet) throws ServletException;
        public <T extends EventListener> T injectListener(T listener) throws ServletException;
    }
}
