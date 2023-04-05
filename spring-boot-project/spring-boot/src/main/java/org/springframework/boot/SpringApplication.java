/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.lang.StackWalker.StackFrame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingSupplier;

/**
 * Class that can be used to bootstrap and launch a Spring application from a
 * Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on
 * your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line
 * arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be
 * called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication {
 *
 * 	// ... Bean definitions
 *
 * 	public static void main(String[] args) {
 * 		SpringApplication.run(MyApplication.class, args);
 * 	}
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be
 * created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different
 * sources. It is
 * generally recommended that a single {@code @Configuration} class is used to
 * bootstrap
 * your application, however, you may also set {@link #getSources() sources}
 * from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by
 * {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by
 * {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 * 
 * Configuration properties are also bound to the {@link SpringApplication}.
 * This makes it
 * possible to set {@link SpringApplication} properties dynamically, like
 * additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web
 * environment
 * ("spring.main.web-application-type=none") or the flag to switch off the
 * banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @author Chris Bono
 * @since 1.0.0
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {


	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	public static final Log logger = LogFactory.getLog(SpringApplication.class);

	public static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();

	private static final ThreadLocal<SpringApplicationHook> applicationHook = new ThreadLocal<>();

	private final Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();

	private ConfigureMain configureMain = new ConfigureMain();

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	private boolean addConversionService = true;

	private Banner banner;

	private ResourceLoader resourceLoader;

	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;

	private List<ApplicationContextInitializer<?>> initializers;

	private List<ApplicationListener<?>> listeners;

	private final List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;

	private Set<String> additionalProfiles = Collections.emptySet();

	private boolean allowBeanDefinitionOverriding;

	private boolean allowCircularReferences;

	private boolean isCustomEnvironment = false;

	private boolean lazyInitialization = false;

	private String environmentPrefix;

	private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;



	/**
	 * Create a new {@link SpringApplication} instance. The application context will
	 * load
	 * beans from the specified primary sources (see {@link SpringApplication
	 * class-level}
	 * documentation for details). The instance can be customized before calling
	 * {@link #run(String...)}.
	 * 
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		this(null, primarySources);
	}

	/**
	 * Create a new {@link SpringApplication} instance. The application context will
	 * load
	 * beans from the specified primary sources (see {@link SpringApplication
	 * class-level}
	 * documentation for details). The instance can be customized before calling
	 * {@link #run(String...)}.
	 * 
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		this.bootstrapRegistryInitializers = new ArrayList<>(
				getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		this.configureMain.setMainApplicationClass(deduceMainApplicationClass());
	}

	public ConfigureMain getConfigureMain() {
		return this.configureMain;
	}

	private Class<?> deduceMainApplicationClass() {
		return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
				.walk(this::findMainClass)
				.orElse(null);
	}

	private Optional<Class<?>> findMainClass(Stream<StackFrame> stack) {
		return stack.filter((frame) -> Objects.equals(frame.getMethodName(), "main"))
				.findFirst()
				.map(StackWalker.StackFrame::getDeclaringClass);
	}

	private DefaultBootstrapContext createBootstrapContext() {
		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
		return bootstrapContext;
	}

	public void addAotGeneratedInitializerIfNecessary(Set<ApplicationContextInitializer<?>> set) {
		if (AotDetector.useGeneratedArtifacts()) {
			List<ApplicationContextInitializer<?>> aotInitializers = new ArrayList<>(
					set.stream().filter(AotApplicationContextInitializer.class::isInstance).toList());
			if (aotInitializers.isEmpty()) {
				String initializerClassName = this.configureMain.getMainApplicationClass().getName() + "__ApplicationContextInitializer";
				aotInitializers.add(AotApplicationContextInitializer.forInitializerClasses(initializerClassName));
			}
			set.removeAll(aotInitializers);
			set.addAll(aotInitializers);
		}
	}

	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS,
				System.getProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

	private SpringApplicationRunListeners getRunListeners(String[] args) {
		ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
		argumentResolver = argumentResolver.and(String[].class, args);
		List<SpringApplicationRunListener> listeners = getSpringFactoriesInstances(SpringApplicationRunListener.class,
				argumentResolver);
		SpringApplicationHook hook = applicationHook.get();
		SpringApplicationRunListener hookListener = (hook != null) ? hook.getRunListener(this) : null;
		if (hookListener != null) {
			listeners = new ArrayList<>(listeners);
			listeners.add(hookListener);
		}
		return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
	}

	private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, null);
	}

	<T> List<T> getSpringFactoriesInstances(Class<T> type, ArgumentResolver argumentResolver) {
		return SpringFactoriesLoader.forDefaultResourceLocation(getClassLoader()).load(type, argumentResolver);
	}

	public boolean getIsCustomEnvironment() {
		return isCustomEnvironment;
	}

	private Banner printBanner(ConfigurableEnvironment environment) {
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		ResourceLoader resourceLoader = (this.resourceLoader != null) ? this.resourceLoader
				: new DefaultResourceLoader(null);
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.configureMain.getMainApplicationClass(), logger);
		}
		return bannerPrinter.print(environment, this.configureMain.getMainApplicationClass(), System.out);
	}

	/**
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * 
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(),
					ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * 
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.configureMain.getMainApplicationClass()).logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information.
	 * 
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			List<String> activeProfiles = quoteProfiles(context.getEnvironment().getActiveProfiles());
			if (ObjectUtils.isEmpty(activeProfiles)) {
				List<String> defaultProfiles = quoteProfiles(context.getEnvironment().getDefaultProfiles());
				String message = String.format("%s default %s: ", defaultProfiles.size(),
						(defaultProfiles.size() <= 1) ? "profile" : "profiles");
				log.info("No active profile set, falling back to " + message
						+ StringUtils.collectionToDelimitedString(defaultProfiles, ", "));
			} else {
				String message = (activeProfiles.size() == 1) ? "1 profile is active: "
						: activeProfiles.size() + " profiles are active: ";
				log.info("The following " + message + StringUtils.collectionToDelimitedString(activeProfiles, ", "));
			}
		}
	}

	private List<String> quoteProfiles(String[] profiles) {
		return Arrays.stream(profiles).map((profile) -> "\"" + profile + "\"").toList();
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * 
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.configureMain.getMainApplicationClass() == null) {
			return logger;
		}
		return LogFactory.getLog(this.configureMain.getMainApplicationClass());
	}

	/**
	 * Load beans into the application context.
	 * 
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		BeanDefinitionLoader loader = createBeanDefinitionLoader(getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * 
	 * @return the resourceLoader the resource loader that will be used in the
	 *         ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set), or the
	 * context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils}
	 * class.
	 * 
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * 
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry registry) {
			return registry;
		}
		if (context instanceof AbstractApplicationContext abstractApplicationContext) {
			return (BeanDefinitionRegistry) abstractApplicationContext.getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * 
	 * @param registry the bean definition registry
	 * @param sources  the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * 
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ConfigurableApplicationContext applicationContext) {
		applicationContext.refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * 
	 * @param context the application context
	 * @param args    the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
	}

	/**
	 * Returns the type of web application that is being run.
	 * 
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of
	 * web
	 * application will be deduced based on the classpath.
	 * 
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same
	 * name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * 
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1.0
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	public boolean setAllowBeanDefinitionOverriding(){
		return this.allowBeanDefinitionOverriding;
	}

	/**
	 * Sets whether to allow circular references between beans and automatically try
	 * to
	 * resolve them. Defaults to {@code false}.
	 * 
	 * @param allowCircularReferences if circular references are allowed
	 * @since 2.6.0
	 * @see AbstractAutowireCapableBeanFactory#setAllowCircularReferences(boolean)
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	public boolean getAllowCircularReferences() {
		return this.allowCircularReferences;
	}

	/**
	 * Sets if beans should be initialized lazily. Defaults to {@code false}.
	 * 
	 * @param lazyInitialization if initialization should be lazy
	 * @since 2.2
	 * @see BeanDefinition#setLazyInit(boolean)
	 */
	public void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	public boolean getLazyInitialization() {
		return this.lazyInitialization;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * 
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * 
	 * @param registerShutdownHook if the shutdown hook should be registered
	 * @see #getShutdownHandlers()
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	public boolean getRegisterShutdownHook() {
		return this.registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when
	 * no
	 * static banner file is provided.
	 * 
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults
	 * to
	 * {@code Banner.Mode.CONSOLE}.
	 * 
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application
	 * starts.
	 * Defaults to {@code true}.
	 * 
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	public boolean getLogStartupInfo() {
		return this.logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the
	 * application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * 
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	public boolean getAddCommandLineProperties() {
		return this.addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the
	 * application
	 * context's {@link Environment}.
	 * 
	 * @param addConversionService if the application conversion service should be
	 *                             added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	public boolean getAddConversionService() {
		return this.addConversionService;
	}

	/**
	 * Adds {@link BootstrapRegistryInitializer} instances that can be used to
	 * initialize
	 * the {@link BootstrapRegistry}.
	 * 
	 * @param bootstrapRegistryInitializer the bootstrap registry initializer to add
	 * @since 2.4.5
	 */
	public void addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
		Assert.notNull(bootstrapRegistryInitializer, "BootstrapRegistryInitializer must not be null");
		this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
	}

	/**
	 * Set additional profile values to use (on top of those set in system or
	 * command line
	 * properties).
	 * 
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
	}

	/**
	 * Return an immutable set of any additional profiles in use.
	 * 
	 * @return the additional profiles
	 */
	public Set<String> getAdditionalProfiles() {
		return this.additionalProfiles;
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * 
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	public BeanNameGenerator getBeanNameGenerator() {
		return this.beanNameGenerator; 
	}

	/**
	 * Sets the underlying environment that should be used with the created
	 * application
	 * context.
	 * 
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most
	 * users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather
	 * than
	 * calling this method.
	 * 
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an
	 * ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * 
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A
	 * source
	 * can be: a class name, package name, or an XML resource location.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * 
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method
	 * combines any
	 * primary sources specified in the constructor with any additional ones that
	 * have
	 * been {@link #setSources(Set) explicitly set}.
	 * 
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}
		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * 
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}


	public ConfigurableEnvironment getEnvironment() {
		return this.environment;
	}

	/**
	 * Return a prefix that should be applied when obtaining configuration
	 * properties from
	 * the system environment.
	 * 
	 * @return the environment property prefix
	 * @since 2.5.0
	 */
	public String getEnvironmentPrefix() {
		return this.environmentPrefix;
	}

	/**
	 * Set the prefix that should be applied when obtaining configuration properties
	 * from
	 * the system environment.
	 * 
	 * @param environmentPrefix the environment property prefix to set
	 * @since 2.5.0
	 */
	public void setEnvironmentPrefix(String environmentPrefix) {
		this.environmentPrefix = environmentPrefix;
	}

	/**
	 * Sets the factory that will be called to create the application context. If
	 * not set,
	 * defaults to a factory that will create
	 * {@link AnnotationConfigServletWebServerApplicationContext} for servlet web
	 * applications, {@link AnnotationConfigReactiveWebServerApplicationContext} for
	 * reactive web applications, and {@link AnnotationConfigApplicationContext} for
	 * non-web applications.
	 * 
	 * @param applicationContextFactory the factory for the context
	 * @since 2.4.0
	 */
	public void setApplicationContextFactory(ApplicationContextFactory applicationContextFactory) {
		this.applicationContextFactory = (applicationContextFactory != null) ? applicationContextFactory
				: ApplicationContextFactory.DEFAULT;
	}

	public ApplicationContextFactory getApplicationContextFactory() {
		return this.applicationContextFactory;
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the
	 * Spring
	 * {@link ApplicationContext}.
	 * 
	 * @param initializers the initializers to set
	 */
	public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * 
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s
	 * that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * 
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the
	 * SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * 
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * 
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will
	 * be
	 * applied to the SpringApplication and registered with the
	 * {@link ApplicationContext}
	 * .
	 * 
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * Set the {@link ApplicationStartup} to use for collecting startup metrics.
	 * 
	 * @param applicationStartup the application startup to use
	 * @since 2.4.0
	 */
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		this.applicationStartup = (applicationStartup != null) ? applicationStartup : ApplicationStartup.DEFAULT;
	}

	/**
	 * Returns the {@link ApplicationStartup} used for collecting startup metrics.
	 * 
	 * @return the application startup
	 * @since 2.4.0
	 */
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	/**
	 * Return a {@link SpringApplicationShutdownHandlers} instance that can be used
	 * to add
	 * or remove handlers that perform actions before the JVM is shutdown.
	 * 
	 * @return a {@link SpringApplicationShutdownHandlers} instance
	 * @since 2.5.1
	 */
	public static SpringApplicationShutdownHandlers getShutdownHandlers() {
		return shutdownHook.getHandlers();
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain
	 * a
	 * code indicating success (0) or otherwise. Does not throw exceptions but
	 * should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator ExitCodeGenerators} in addition to any Spring beans
	 * that
	 * implement {@link ExitCodeGenerator}. When multiple generators are available,
	 * the
	 * first non-zero exit code is used. Generators are ordered based on their
	 * {@link Ordered} implementation and {@link Order @Order} annotation.
	 * 
	 * @param context            the context to close if possible
	 * @param exitCodeGenerators exit code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			} finally {
				close(context);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	/**
	 * Perform the given action with the given {@link SpringApplicationHook}
	 * attached if
	 * the action triggers an {@link SpringApplication#run(String...) application
	 * run}.
	 * 
	 * @param hook   the hook to apply
	 * @param action the action to run
	 * @since 3.0.0
	 * @see #withHook(SpringApplicationHook, ThrowingSupplier)
	 */
	public static void withHook(SpringApplicationHook hook, Runnable action) {
		withHook(hook, () -> {
			action.run();
			return null;
		});
	}

	/**
	 * Perform the given action with the given {@link SpringApplicationHook}
	 * attached if
	 * the action triggers an {@link SpringApplication#run(String...) application
	 * run}.
	 * 
	 * @param <T>    the result type
	 * @param hook   the hook to apply
	 * @param action the action to run
	 * @return the result of the action
	 * @since 3.0.0
	 * @see #withHook(SpringApplicationHook, Runnable)
	 */
	public static <T> T withHook(SpringApplicationHook hook, ThrowingSupplier<T> action) {
		applicationHook.set(hook);
		try {
			return action.get();
		} finally {
			applicationHook.remove();
		}
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext closable) {
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

	/**
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * 
	 * @param args the application arguments (usually passed from a Java main
	 *             method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		long startTime = System.nanoTime();
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		configureHeadlessProperty();
		SpringApplicationRunListeners listeners = getRunListeners(args);
		listeners.starting(bootstrapContext, this.configureMain.getMainApplicationClass());
		Failure failure = new Failure(this);
		try {
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);

			ConfigureEnvironment configureEnv = new ConfigureEnvironment(this);
			ConfigurableEnvironment environment = configureEnv.prepareEnvironment(listeners, bootstrapContext,
					applicationArguments);
			Banner printedBanner = printBanner(environment);
			ConfigurableContext configurableContext = new ConfigurableContext(this);
			context = configurableContext.createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
			configurableContext.prepareContext(bootstrapContext, context, environment, listeners, applicationArguments,
					printedBanner);
			configurableContext.refreshContext(context);
			afterRefresh(context, applicationArguments);
			Duration timeTakenToStartup = Duration.ofNanos(System.nanoTime() - startTime);
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.configureMain.getMainApplicationClass()).logStarted(getApplicationLog(), timeTakenToStartup);
			}
			listeners.started(context, timeTakenToStartup);

			Runner runner = new Runner();
			runner.callRunners(context, applicationArguments);

		} catch (Throwable ex) {
			if (ex instanceof AbandonedRunException) {
				throw ex;
			}
			failure.handleRunFailure(context, ex, listeners);
			throw new IllegalStateException(ex);
		}
		try {
			if (context.isRunning()) {
				Duration timeTakenToReady = Duration.ofNanos(System.nanoTime() - startTime);
				listeners.ready(context, timeTakenToReady);
			}
		} catch (Throwable ex) {
			if (ex instanceof AbandonedRunException) {
				throw ex;
			}
			failure.handleRunFailure(context, ex, null);
			throw new IllegalStateException(ex);
		}
		return context;
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings.
	 * 
	 * @param primarySource the primary source to load
	 * @param args          the application arguments (usually passed from a Java
	 *                      main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
		return run(new Class<?>[] { primarySource }, args);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 * 
	 * @param primarySources the primary sources to load
	 * @param args           the application arguments (usually passed from a Java
	 *                       main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful
	 * when
	 * application sources are defined through a {@literal --spring.main.sources}
	 * command
	 * line argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * 
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

}
