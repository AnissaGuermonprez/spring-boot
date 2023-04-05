package org.springframework.boot;


import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.util.ReflectionUtils;
public class Failure {

    private Log logger; 
    private SpringApplicationShutdownHook shutdownHook;
    private SpringApplication springApplication;

    public Failure(SpringApplication springApplication) {
        this.springApplication = springApplication;
        this.logger = SpringApplication.logger; 
        this.shutdownHook = SpringApplication.shutdownHook;

    }

    void handleRunFailure(ConfigurableApplicationContext context, Throwable exception,
            SpringApplicationRunListeners listeners) {
        try {
            try {
                handleExitCode(context, exception);
                if (listeners != null) {
                    listeners.failed(context, exception);
                }
            } finally {
                reportFailure(getExceptionReporters(context), exception);
                if (context != null) {
                    context.close();
                    shutdownHook.deregisterFailedApplicationContext(context);
                }
            }
        } catch (Exception ex) {
            logger.warn("Unable to close ApplicationContext", ex);
        }
        ReflectionUtils.rethrowRuntimeException(exception);
    }

    private Collection<SpringBootExceptionReporter> getExceptionReporters(ConfigurableApplicationContext context) {
        try {
            ArgumentResolver argumentResolver = ArgumentResolver.of(ConfigurableApplicationContext.class, context);
            return this.springApplication.getSpringFactoriesInstances(SpringBootExceptionReporter.class, argumentResolver);
        } catch (Throwable ex) {
            return Collections.emptyList();
        }
    }

    private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
        try {
            for (SpringBootExceptionReporter reporter : exceptionReporters) {
                if (reporter.reportException(failure)) {
                    registerLoggedException(failure);
                    return;
                }
            }
        } catch (Throwable ex) {
            // Continue with normal handling of the original failure
        }
        if (logger.isErrorEnabled()) {
            logger.error("Application run failed", failure);
            registerLoggedException(failure);
        }
    }

    /**
     * Register that the given exception has been logged. By default, if the running
     * in
     * the main thread, this method will suppress additional printing of the
     * stacktrace.
     * 
     * @param exception the exception that was logged
     */
    protected void registerLoggedException(Throwable exception) {
        SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
        if (handler != null) {
            handler.registerLoggedException(exception);
        }
    }

    private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromException(context, exception);
        if (exitCode != 0) {
            if (context != null) {
                context.publishEvent(new ExitCodeEvent(context, exitCode));
            }
            SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
            if (handler != null) {
                handler.registerExitCode(exitCode);
            }
        }
    }

    private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = getExitCodeFromMappedException(context, exception);
        if (exitCode == 0) {
            exitCode = getExitCodeFromExitCodeGeneratorException(exception);
        }
        return exitCode;
    }

    private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
        if (context == null || !context.isActive()) {
            return 0;
        }
        ExitCodeGenerators generators = new ExitCodeGenerators();
        Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
        generators.addAll(exception, beans);
        return generators.getExitCode();
    }

    private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
        if (exception == null) {
            return 0;
        }
        if (exception instanceof ExitCodeGenerator generator) {
            return generator.getExitCode();
        }
        return getExitCodeFromExitCodeGeneratorException(exception.getCause());
    }

    SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (new ConfigureMain().isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}


}
