package org.springframework.boot;

import org.springframework.context.ConfigurableApplicationContext;

/**
	 * Exception that can be thrown to silently exit a running {@link SpringApplication}
	 * without handling run failures.
	 *
	 * @since 3.0.0
	 */
	public class AbandonedRunException extends RuntimeException {

		private final ConfigurableApplicationContext applicationContext;

		/**
		 * Create a new {@link AbandonedRunException} instance.
		 */
		public AbandonedRunException() {
			this(null);
		}

		/**
		 * Create a new {@link AbandonedRunException} instance with the given application
		 * context.
		 * @param applicationContext the application context that was available when the
		 * run was abandoned
		 */
		public AbandonedRunException(ConfigurableApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
		}

		/**
		 * Return the application context that was available when the run was abandoned or
		 * {@code null} if no context was available.
		 * @return the application context
		 */
		public ConfigurableApplicationContext getApplicationContext() {
			return this.applicationContext;
		}

	}