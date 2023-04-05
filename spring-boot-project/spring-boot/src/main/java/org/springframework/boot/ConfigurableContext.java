package org.springframework.boot;

import java.util.Set;

import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;

public class ConfigurableContext {

    public SpringApplication springApplication;

    public ConfigurableContext(SpringApplication springApplication) {
        this.springApplication = springApplication;
    }

    public void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
            ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
            ApplicationArguments applicationArguments, Banner printedBanner) {
        context.setEnvironment(environment);
        postProcessApplicationContext(context);
        this.springApplication.addAotGeneratedInitializerIfNecessary(this.springApplication.getInitializers());
        this.springApplication.applyInitializers(context);
        listeners.contextPrepared(context);
        bootstrapContext.close(context);
        if (this.springApplication.getLogStartupInfo()) {
            this.springApplication.logStartupInfo(context.getParent() == null);
            this.springApplication.logStartupProfileInfo(context);
        }
        // Add boot specific singleton beans
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
        if (printedBanner != null) {
            beanFactory.registerSingleton("springBootBanner", printedBanner);
        }
        if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
            autowireCapableBeanFactory.setAllowCircularReferences(this.springApplication.getAllowCircularReferences());
            if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
                listableBeanFactory.setAllowBeanDefinitionOverriding(this.springApplication.setAllowBeanDefinitionOverriding());
            }
        }
        if (this.springApplication.getLazyInitialization()) {
            context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
        }
        context.addBeanFactoryPostProcessor(new PropertySourceOrderingBeanFactoryPostProcessor(context));
        if (!AotDetector.useGeneratedArtifacts()) {
            // Load the sources
            Set<Object> sources = this.springApplication.getAllSources();
            Assert.notEmpty(sources, "Sources must not be empty");
            this.springApplication.load(context, sources.toArray(new Object[0]));
        }
        listeners.contextLoaded(context);
    }

    public void refreshContext(ConfigurableApplicationContext context) {
        if (this.springApplication.getRegisterShutdownHook()) {
            SpringApplication.getShutdownHandlers().registerApplicationContext(context);
        }
        this.springApplication.refresh(context);
    }

    /**
     * Strategy method used to create the {@link ApplicationContext}. By default
     * this
     * method will respect any explicitly set application context class or factory
     * before
     * falling back to a suitable default.
     * 
     * @return the application context (not yet refreshed)
     * @see #setApplicationContextFactory(ApplicationContextFactory)
     */
    protected ConfigurableApplicationContext createApplicationContext() {
        return this.springApplication.getApplicationContextFactory().create(this.springApplication.getWebApplicationType());
    }

    /**
     * Apply any relevant post processing the {@link ApplicationContext}. Subclasses
     * can
     * apply additional processing as required.
     * 
     * @param context the application context
     */
    protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        if (this.springApplication.getBeanNameGenerator() != null) {
            context.getBeanFactory()
                    .registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, this.springApplication.getBeanNameGenerator());
        }
        if (this.springApplication.getResourceLoader() != null) {
            if (context instanceof GenericApplicationContext genericApplicationContext) {
                genericApplicationContext.setResourceLoader(this.springApplication.getResourceLoader());
            }
            if (context instanceof DefaultResourceLoader defaultResourceLoader) {
                defaultResourceLoader.setClassLoader(this.springApplication.getResourceLoader().getClassLoader());
            }
        }
        if (this.springApplication.getAddConversionService()) {
            context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
        }
    }

    /**
     * {@link BeanFactoryPostProcessor} to re-order our property sources below any
     * {@code @PropertySource} items added by the
     * {@link ConfigurationClassPostProcessor}.
     */
    private static class PropertySourceOrderingBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

        private final ConfigurableApplicationContext context;

        PropertySourceOrderingBeanFactoryPostProcessor(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            DefaultPropertiesPropertySource.moveToEnd(this.context.getEnvironment());
        }

    }

}
