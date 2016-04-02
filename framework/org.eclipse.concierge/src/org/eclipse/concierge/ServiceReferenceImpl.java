/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Jan S. Rellermeyer, IBM Research - initial API and implementation
 *******************************************************************************/
package org.eclipse.concierge;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Jan S. Rellermeyer
 */
final class ServiceReferenceImpl<S> implements ServiceReference<S> {
	/**
	 * the framework
	 */
	protected final Concierge framework;

	/**
	 * the bundle object.
	 */
	Bundle bundle;

	/**
	 * the service object.
	 */
	protected S service;

	/**
	 * the service properties.
	 */
	final Map<String, Object> properties;

	/**
	 * the bundles that are using the service.
	 */
	final Map<Bundle, Integer> useCounters = new HashMap<Bundle, Integer>(0);

	/**
	 * cached service objects if the registered service is a service factory.
	 */
	HashMap<Bundle, S> cachedServices = null;

	/** 
	 * service objects instance
	 */
	HashMap<Bundle, ServiceObjectsImpl> serviceObjects = null; 
	
	/**
	 * the registration.
	 */
	ServiceRegistration<S> registration;

	/**
	 * the next service id.
	 */
	private static long nextServiceID = 0;

	final boolean isServiceFactory;
	final boolean isPrototype;

	/**
	 * these service properties must not be overwritten by property updates.
	 */
	protected final static HashSet<String> forbidden;
	static {
		forbidden = new HashSet<String>(3);
		forbidden.add(Constants.SERVICE_ID.toLowerCase());
		forbidden.add(Constants.SERVICE_BUNDLEID.toLowerCase());
		forbidden.add(Constants.OBJECTCLASS.toLowerCase());
	}

	/**
	 * create a new service reference implementation instance.
	 * 
	 * @param bundle
	 *            the bundle.
	 * @param service
	 *            the service object.
	 * @param props
	 *            the service properties.
	 * @param clazzes
	 *            the interface classes that the service is registered under.
	 * @throws ClassNotFoundException
	 */
	ServiceReferenceImpl(final Concierge framework, final Bundle bundle,
			final S service, final Dictionary<String, ?> props,
			final String[] clazzes) {
		String scope = "singleton";
		if (service instanceof PrototypeServiceFactory) {
			isServiceFactory = true;
			isPrototype = true;
			scope = "prototype";
		} else if(service instanceof ServiceFactory) {
			isServiceFactory = true;
			isPrototype = false;
			scope = "bundle";
		} else {
			isServiceFactory = false;
			isPrototype = false;
			checkService(service, clazzes);
		}

		this.framework = framework;
		this.bundle = bundle;
		this.service = service;
		this.properties = new HashMap<String, Object>(props == null ? 5
				: props.size() + 5);
		if (props != null) {
			for (final Enumeration<String> keys = props.keys(); keys
					.hasMoreElements();) {
				final String key = keys.nextElement();
				properties.put(key, props.get(key));
			}
		}
		properties.put(Constants.OBJECTCLASS, clazzes);
		properties.put(Constants.SERVICE_BUNDLEID, bundle.getBundleId());
		properties.put(Constants.SERVICE_ID, new Long(++nextServiceID));
		final Integer ranking = props == null ? null : (Integer) props
				.get(Constants.SERVICE_RANKING);
		properties.put(Constants.SERVICE_RANKING,
				ranking == null ? new Integer(0) : ranking);
		properties.put(Constants.SERVICE_SCOPE, scope);
		this.registration = new ServiceRegistrationImpl();
	}

	private void checkService(final Object service, final String[] clazzes)
			throws IllegalArgumentException {
		if (service == null) {
			throw new IllegalArgumentException("ServiceFactory produced /null/");
		}
		for (int i = 0; i < clazzes.length; i++) {
			try {
				final Class<?> current = Class.forName(clazzes[i], false,
						service.getClass().getClassLoader());
				if (!current.isInstance(service)) {
					throw new IllegalArgumentException("Service "
							+ service.getClass().getName()
							+ " does not implement the interface " + clazzes[i]);
				}
			} catch (final ClassNotFoundException e) {
				throw new IllegalArgumentException("Interface " + clazzes[i]
						+ " implemented by service "
						+ service.getClass().getName() + " cannot be located: "
						+ e.getMessage());
			}
		}
	}

	void invalidate() {
		service = null;
		useCounters.clear();
		bundle = null;
		registration = null;
		if (cachedServices != null) {
			cachedServices = null;
		}
	}

	/**
	 * get the bundle that has registered the service.
	 * 
	 * @return the bundle object.
	 * @see org.osgi.framework.ServiceReference#getBundle()
	 * @category ServiceReference
	 */
	public Bundle getBundle() {
		return bundle;
	}

	/**
	 * get a property.
	 * 
	 * @param key
	 *            the key.
	 * @return the value or null, if the entry does not exist.
	 * @see org.osgi.framework.ServiceReference#getProperty(java.lang.String)
	 * @category ServiceReference
	 */
	public Object getProperty(final String key) {
		// first, try the original case
		Object result = properties.get(key);
		if (result != null) {
			return result;
		}

		// then, try the lower case variant
		result = properties.get(key.toLowerCase());
		if (result != null) {
			return result;
		}

		// bad luck, try case insensitive matching of the keys
		for (final String k : properties.keySet()) {
			if (k.equalsIgnoreCase(key)) {
				result = properties.get(k);
				break;
			}
		}
		return result;
	}

	/**
	 * get all property keys.
	 * 
	 * @return the array of all keys.
	 * @see org.osgi.framework.ServiceReference#getPropertyKeys()
	 * @category ServiceReference
	 */
	public String[] getPropertyKeys() {
		return properties.keySet().toArray(new String[properties.size()]);
	}

	/**
	 * get the using bundles.
	 * 
	 * @return the array of all bundles.
	 * @see org.osgi.framework.ServiceReference#getUsingBundles()
	 * @category ServiceReference
	 */
	public Bundle[] getUsingBundles() {
		synchronized (useCounters) {
			if (useCounters.isEmpty()) {
				return null;
			}
			return useCounters.keySet().toArray(new Bundle[useCounters.size()]);
		}
	}

	// FIXME: concurrency???
	private boolean marker = false;

	/**
	 * get the service object. If the service is a service factory, a cached
	 * value might be returned.
	 * 
	 * @param theBundle
	 *            the requesting bundle.
	 * @return the service object.
	 */
	S getService(final Bundle theBundle) {
		if (service == null || marker) {
			return null;
		}

		synchronized (useCounters) {
			if (isServiceFactory) {
				if (cachedServices == null) {
					cachedServices = new HashMap<Bundle, S>(1);
				}
				final S cachedService = cachedServices.get(theBundle);
				if (cachedService != null) {
					incrementCounter(theBundle);
					return cachedService;
				}
				S s = factorService(theBundle, true);
				cachedServices.put(theBundle, s);
				return s;
			}

			incrementCounter(theBundle);

			return service;
		}
	}
	
	S factorService(final Bundle theBundle, boolean count){
		@SuppressWarnings("unchecked")
		final ServiceFactory<S> factory = (ServiceFactory<S>) service;
		final S factoredService;
		try {
			if(count)
				incrementCounter(theBundle);
			marker = true;
			factoredService = factory.getService(theBundle,
					registration);
			marker = false;
			checkService(factoredService,
					(String[]) properties.get(Constants.OBJECTCLASS));
			// catch failed check and exceptions thrown in factory
		} catch (final IllegalArgumentException iae) {
			if(count)
				decrementCounter(theBundle);
			framework.notifyFrameworkListeners(FrameworkEvent.ERROR,
					bundle, new ServiceException(
							"Invalid service object",
							ServiceException.FACTORY_ERROR));
			return null;
		} catch (final Throwable t) {
			if(count)
				decrementCounter(theBundle);
			framework.notifyFrameworkListeners(FrameworkEvent.ERROR,
					bundle, new ServiceException(
							"Exception while factoring the service",
							ServiceException.FACTORY_EXCEPTION, t));
			return null;
		}
		
		return factoredService;
	}

	private void incrementCounter(final Bundle theBundle) {
		Integer counter = useCounters.get(theBundle);
		if (counter == null) {
			counter = new Integer(1);
		} else {
			counter = new Integer(counter.intValue() + 1);
		}
		useCounters.put(theBundle, counter);
	}

	void decrementCounter(final Bundle theBundle) {
		Integer counter = useCounters.get(theBundle);
		final int newValue = counter.intValue() - 1;
		if (newValue == 0) {
			counter = null;
		} else {
			counter = new Integer(newValue);
		}
		useCounters.put(theBundle, counter);
	}

	/**
	 * unget the service.
	 * 
	 * @param theBundle
	 *            the using bundle.
	 * @return <tt>false</tt> if the context bundle's use count for the service
	 *         is zero or if the service has been unregistered; <tt>true</tt>
	 *         otherwise.
	 */
	@SuppressWarnings("unchecked")
	boolean ungetService(final Bundle theBundle) {
		synchronized (useCounters) {
			if (service == null) {
				return false;
			}
			Integer counter = useCounters.get(theBundle);
			if (counter == null) {
				return false;
			}
			if (counter.intValue() == 1) {
				if (isServiceFactory) {
					try {
						((ServiceFactory<S>) service).ungetService(theBundle,
								registration, cachedServices.get(theBundle));
						// catch exceptions thrown in factory
					} catch (final Throwable t) {
						framework.notifyFrameworkListeners(
								FrameworkEvent.ERROR, bundle, t);
					}
					cachedServices.remove(theBundle);
				} 
				if(isPrototype){
					// check if there is a serviceobjects that could also have used service instances
					if(serviceObjects != null){
						ServiceObjectsImpl so = serviceObjects.get(theBundle);
						if(so != null){
							if(so.services.size() != 0){
								useCounters.put(theBundle, new Integer(0));
								return true;
							}
						}
					}
				}			
				useCounters.remove(theBundle);
				return true;
			} else if(counter.intValue()== 0){
				// prototype services in use don't count here...
				return false;
			} else {
				counter = new Integer(counter.intValue() - 1);
				useCounters.put(theBundle, counter);
				return true;
			}
		}
	}
	
	void ungetAllServices(Bundle bundle){
		ServiceObjectsImpl so = serviceObjects.get(bundle);
		if(so!=null){
			so.ungetAllServices();
		}
		ungetService(bundle);
		
	}

	@SuppressWarnings("unchecked")
	ServiceObjects<S> getServiceObjects(final Bundle bundle){
		if(service == null)
			return null;  // service already unregistered
		
		if(serviceObjects==null){
			serviceObjects = new HashMap<Bundle, ServiceObjectsImpl>(1);
		}
		
		ServiceObjectsImpl so = serviceObjects.get(bundle);
		if(so == null){
			so = new ServiceObjectsImpl(bundle);
			serviceObjects.put(bundle, so);
		}
		return so;
	}
	
	/**
	 * get a string representation of the service reference implementation.
	 * 
	 * @return the string.
	 * @category Object
	 */
	public String toString() {
		return "ServiceReference{" + service + "}";
	}

	/**
	 * compare service references.
	 * 
	 * @param reference
	 *            , the reference to compare to
	 * @return integer , return value < 0 if this < reference return value = 0
	 *         if this = reference return value > 0 if tis > reference
	 * @see org.osgi.framework.ServiceReference#compareTo(Object)
	 * @category ServiceReference
	 */
	public int compareTo(final Object reference) {
		if (!(reference instanceof ServiceReferenceImpl)
				|| ((ServiceReferenceImpl<?>) reference).framework != framework) {
			throw new IllegalArgumentException(
					"ServiceReference was not created by the same framework instance");
		}

		final ServiceReferenceImpl<?> other = (ServiceReferenceImpl<?>) reference;
		final int comparedServiceIds = ((Long) properties
				.get(Constants.SERVICE_ID)).compareTo((Long) other.properties
				.get(Constants.SERVICE_ID));
		if (comparedServiceIds == 0) {
			return 0;
		}
		final int res = ((Integer) properties.get(Constants.SERVICE_RANKING))
				.compareTo((Integer) other.properties
						.get(Constants.SERVICE_RANKING));
		if (res < 0) {
			return -1;
		} else if (res > 0) {
			return 1;
		}
		if (comparedServiceIds < 0) {
			return 1;
		} else {
			return -1;
		}

	}

	/**
	 * test if bundle and class have same source
	 * 
	 * @param theBundle
	 *            the bundle
	 * @param className
	 *            the class name
	 * @return true if bundle and class have same source
	 * @see org.osgi.framework.ServiceReference#isAssignableTo(Bundle, String)
	 * @category ServiceReference
	 */
	public boolean isAssignableTo(final Bundle theBundle, final String className) {
		// if the bundle is the one that registered the service, we are done
		if (theBundle == bundle || bundle == framework
				|| theBundle == framework) {
			return true;
		}

		final BundleImpl otherBundle = (BundleImpl) theBundle;
		final BundleImpl ourBundle = (BundleImpl) bundle;

		try {
			return otherBundle.loadClass(className) == ourBundle
					.loadClass(className);
		} catch (final ClassNotFoundException e) {
			return true;
		}
	}

	
	/**
	 * Class for ServiceObjects in case you want multiple instances for a prototype
	 * scoped service
	 *  
	 * @author tverbele
	 *
	 */
	private final class ServiceObjectsImpl implements 
		ServiceObjects<S>{
		
		final Set<S> services = new HashSet<S>();
		private final Bundle b;
		
		public ServiceObjectsImpl(final Bundle bundle){
			this.b = bundle;
		}
		
		public S getService() {
			if(isPrototype){
				S factoredService = null;
				synchronized(useCounters){
					factoredService = factorService(b, false);
					services.add(factoredService);
					if(!useCounters.containsKey(b)){
						// mark 0 use count for prototype services
						// this makes sure that the bundle is marked as usingBundle
						useCounters.put(b, new Integer(0)); 
					}
				}
				return factoredService;
			}
			
			return (S) ServiceReferenceImpl.this.getService(b);
		}

		public void ungetService(S s) {
			if(isPrototype){
				if(services.remove(s)) {
					try {
						final ServiceFactory<S> factory = (ServiceFactory<S>) service;
						factory.ungetService(b, (ServiceRegistration<S>) registration, s);
					} catch (final Throwable t) {
						framework.notifyFrameworkListeners(
							FrameworkEvent.ERROR, b, t);
					}
					if(services.size() == 0){
						if(useCounters.get(b) == 0){
							useCounters.remove(b);
						}
					}
				} else {
					throw new IllegalArgumentException("Service object was not provided "
						+ "by this ServiceObjects instance");
				}
			} else {
				// in case of bundle scope, only unget if this is from the right bundle
				if(isServiceFactory) { 
					if(s != cachedServices.get(b)){
						throw new IllegalArgumentException("Service object was not provided "
								+ "by this ServiceObjects instance");
					}
				}
				ServiceReferenceImpl.this.ungetService(b);
			}
		}

		public void ungetAllServices(){
			for(S s : new HashSet<S>(services)){
				ungetService(s);
			}
		}
		
		public ServiceReference<S> getServiceReference() {
			return (ServiceReference<S>) ServiceReferenceImpl.this;
		}
	}
	
	/**
	 * The service registration. It is a private inner class since this entity
	 * is just once returned to the registrar and never retrieved again. It is
	 * more an additional facet of the service than a separate entity.
	 * 
	 * @author Jan S. Rellermeyer
	 */
	private final class ServiceRegistrationImpl implements
			ServiceRegistration<S> {

		protected ServiceRegistrationImpl() {

		}

		/**
		 * get the service reference.
		 * 
		 * @return the service reference.
		 * @see org.osgi.framework.ServiceRegistration#getReference()
		 * @category ServiceRegistration
		 */
		public ServiceReference<S> getReference() {
			if (service == null) {
				throw new IllegalStateException(
						"Service has already been uninstalled");
			}
			return ServiceReferenceImpl.this;
		}

		/**
		 * set some new service properties.
		 * 
		 * @param newProps
		 *            the new service properties.
		 * @see org.osgi.framework.ServiceRegistration#setProperties(java.util.Dictionary)
		 * @category ServiceRegistration
		 */
		public void setProperties(final Dictionary<String, ?> newProps) {
			/*
			 * The values for service.id and objectClass must not be overwritten
			 */
			if (service == null) {
				throw new IllegalStateException(
						"Service has already been uninstalled");
			}

			final Map<String, Object> oldProps;
			// could be called from multiple threads
			synchronized(properties){
				oldProps = new HashMap<String, Object>(
						properties);
	
				final HashMap<String, String> cases = new HashMap<String, String>(
						properties.size());
				for (final String key : properties.keySet()) {
					final String lower = key.toLowerCase();
					if (cases.containsKey(lower)) {
						throw new IllegalArgumentException(
								"Properties contain the same key in different case variants");
					}
					cases.put(lower, key);
				}
				for (final Enumeration<String> keys = newProps.keys(); keys
						.hasMoreElements();) {
					final String key = keys.nextElement();
					final Object value = newProps.get(key);
					final String lower = key.toLowerCase();
	
					if (!forbidden.contains(lower)) {
						final Object existing = cases.get(lower);
						if (existing != null) {
							if (existing.equals(key)) {
								properties.remove(existing);
							} else {
								throw new IllegalArgumentException(
										"Properties already exists in a different case variant");
							}
						}
						properties.put(key, value);
					}
				}
			}

			framework.notifyServiceListeners(ServiceEvent.MODIFIED,
					ServiceReferenceImpl.this, oldProps);
		}

		/**
		 * unregister the service.
		 * 
		 * @see org.osgi.framework.ServiceRegistration#unregister()
		 * @category ServiceRegistration
		 */
		public void unregister() {
			if (service == null) {
				throw new IllegalStateException(
						"Service has already been uninstalled");
			}

			if(cachedServices != null){
				for(Bundle b : cachedServices.keySet()){
					try {
						((ServiceFactory<S>) service).ungetService(b,
							registration, cachedServices.get(b));
						// catch exceptions thrown in factory
					} catch (final Throwable t) {
						framework.notifyFrameworkListeners(
								FrameworkEvent.ERROR, bundle, t);
					}
				}
			}
			
			if(isPrototype && serviceObjects != null){
				for(Bundle b : serviceObjects.keySet()){
					ServiceObjectsImpl so = serviceObjects.get(b);
					for(S serviceInstance : so.services){
						try {
							((ServiceFactory<S>) service).ungetService(b,
								registration, serviceInstance);
							// catch exceptions thrown in factory
						} catch (final Throwable t) {
							framework.notifyFrameworkListeners(
									FrameworkEvent.ERROR, bundle, t);
						}
					}
				}
			}
			
			framework.unregisterService(ServiceReferenceImpl.this);
			service = null;
		}
	}

	boolean isAssignableTo(final AbstractBundle otherBundle,
			final String[] clazzes) {
		for (int j = 0; j < clazzes.length; j++) {
			if (!isAssignableTo(otherBundle, clazzes[j])) {
				return false;
			}
		}
		return true;
	}
}
