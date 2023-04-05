package org.springframework.boot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ConfigureEnvironment {



    private Map<String, Object> defaultProperties;
    private SpringApplication springApplication; 

    public ConfigureEnvironment(SpringApplication springApplication) {
        this.springApplication = springApplication;
    }

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}


    public ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
            DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
        // Create and configure the environment
        ConfigurableEnvironment environment = getOrCreateEnvironment();
        configureEnvironment(environment, applicationArguments.getSourceArgs());
        ConfigurationPropertySources.attach(environment);
        listeners.environmentPrepared(bootstrapContext, environment);
        DefaultPropertiesPropertySource.moveToEnd(environment);
        Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
                "Environment prefix cannot be set via properties.");
        bindToSpringApplication(environment);
        if (!this.springApplication.getIsCustomEnvironment()) {
            EnvironmentConverter environmentConverter = new EnvironmentConverter(this.springApplication.getClassLoader());
            environment = environmentConverter.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
        }
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private ConfigurableEnvironment getOrCreateEnvironment() {
        if (this.springApplication.getEnvironment() != null) {
            return this.springApplication.getEnvironment();
        }
        ConfigurableEnvironment environment = this.springApplication.getApplicationContextFactory().createEnvironment(this.springApplication.getWebApplicationType());
        if (environment == null && this.springApplication.getApplicationContextFactory() != ApplicationContextFactory.DEFAULT) {
            environment = ApplicationContextFactory.DEFAULT.createEnvironment(this.springApplication.getWebApplicationType());
        }
        return (environment != null) ? environment : new ApplicationEnvironment();
    }

    /**
     * Bind the environment to the {@link SpringApplication}.
     * 
     * @param environment the environment to bind
     */
    private void bindToSpringApplication(ConfigurableEnvironment environment) {
        try {
            Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot bind to SpringApplication", ex);
        }
    }

    private Class<? extends ConfigurableEnvironment> deduceEnvironmentClass() {
        Class<? extends ConfigurableEnvironment> environmentType = this.springApplication.getApplicationContextFactory()
                .getEnvironmentType(this.springApplication.getWebApplicationType());
        if (environmentType == null && this.springApplication.getApplicationContextFactory() != ApplicationContextFactory.DEFAULT) {
            environmentType = ApplicationContextFactory.DEFAULT.getEnvironmentType(this.springApplication.getWebApplicationType());
        }
        if (environmentType == null) {
            return ApplicationEnvironment.class;
        }
        return environmentType;
    }

    /**
     * Template method delegating to
     * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
     * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
     * Override this method for complete control over Environment customization, or
     * one of
     * the above for fine-grained control over property sources or profiles,
     * respectively.
     * 
     * @param environment this application's environment
     * @param args        arguments passed to the {@code run} method
     * @see #configureProfiles(ConfigurableEnvironment, String[])
     * @see #configurePropertySources(ConfigurableEnvironment, String[])
     */
    protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
        if (this.springApplication.getAddConversionService()) {
            environment.setConversionService(new ApplicationConversionService());
        }
        configurePropertySources(environment, args);
        configureProfiles(environment, args);
    }

    /*
     * Add, remove or re-order any {@link PropertySource}s in this application's
     * environment.
     * 
     * @param environment this application's environment
     * 
     * @param args arguments passed to the {@code run} method
     * 
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
        MutablePropertySources sources = environment.getPropertySources();
        if (!CollectionUtils.isEmpty(this.defaultProperties)) {
            DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
        }
        if (this.springApplication.getAddCommandLineProperties() && args.length > 0) {
            String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
            if (sources.contains(name)) {
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite
                        .addPropertySource(
                                new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            } else {
                sources.addFirst(new SimpleCommandLinePropertySource(args));
            }
        }
    }

    /**
     * Configure which profiles are active (or active by default) for this
     * application
     * environment. Additional profiles may be activated during configuration file
     * processing through the {@code spring.profiles.active} property.
     * 
     * @param environment this application's environment
     * @param args        arguments passed to the {@code run} method
     * @see #configureEnvironment(ConfigurableEnvironment, String[])
     */
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
    }
}
